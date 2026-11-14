#!/usr/bin/env python3
"""Install the official, checksum-verified Pleos image using sparse files.

The large system.img contains zero-filled space. Keeping that space sparse avoids
allocating its full logical size on the host. No SDK binaries are modified.
"""
import copy
import hashlib
import json
import os
from pathlib import Path
from xml.etree import ElementTree as ET
from zipfile import ZipFile

PROJECT = Path(__file__).resolve().parents[1]
DOWNLOADS = PROJECT / '.dev/downloads'
SDK = Path(os.environ.get('ANDROID_HOME', Path.home() / 'Library/Android/sdk'))
PACKAGE = 'system-images;android-34;pleos_car_emulator;arm64-v8a'
DESTINATION = SDK / Path(*PACKAGE.split(';'))

repository = ET.fromstring((DOWNLOADS / 'pleos-system-images.xml').read_bytes())
package = repository.find('remotePackage')
archive = DOWNLOADS / 'crp_car_arm64_emulator.zip'
expected_size = int(package.findtext('./archives/archive/complete/size'))
expected_sha1 = package.findtext('./archives/archive/complete/checksum').strip()
if not archive.exists() and (DESTINATION / 'package.xml').exists():
    raise SystemExit(f'Already installed; download cache has been cleaned: {DESTINATION}')
if not archive.exists():
    raise SystemExit('Download the official image ZIP into .dev/downloads before running this installer')
assert archive.stat().st_size == expected_size, 'Download is incomplete'
digest = hashlib.sha1()
with archive.open('rb') as stream:
    while block := stream.read(8 * 1024 * 1024):
        digest.update(block)
assert digest.hexdigest() == expected_sha1, 'Official archive checksum mismatch'

installed_report = PROJECT / '.dev/pleos-image-install.json'
already_extracted = installed_report.exists()
if (DESTINATION / 'package.xml').exists() and not already_extracted:
    raise SystemExit(f'Image already registered: {DESTINATION}')
DESTINATION.mkdir(parents=True, exist_ok=True)
zero_block = bytes(1024 * 1024)
with ZipFile(archive) as bundle:
    for entry in ([] if already_extracted else bundle.infolist()):
        relative = Path(entry.filename).relative_to('crp_avd_out_arm64-v8a')
        target = DESTINATION / relative
        if target.resolve() != DESTINATION.resolve() and DESTINATION.resolve() not in target.resolve().parents:
            raise ValueError(f'Unsafe ZIP path: {entry.filename}')
        if entry.is_dir():
            target.mkdir(parents=True, exist_ok=True)
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        with bundle.open(entry) as src, target.open('wb') as out:
            while block := src.read(len(zero_block)):
                if block == zero_block or not block.strip(b'\0'):
                    out.seek(len(block), 1)
                else:
                    out.write(block)
            out.truncate(entry.file_size)
        mode = (entry.external_attr >> 16) & 0o777
        if mode:
            target.chmod(mode)
        print(f'Installed {relative} ({entry.file_size} logical bytes)', flush=True)

# Register the same package metadata supplied by the official SDK repository.
ET.register_namespace('repo', 'http://schemas.android.com/repository/android/common/02')
ET.register_namespace('xsi', 'http://www.w3.org/2001/XMLSchema-instance')
local_repo = ET.Element('{http://schemas.android.com/repository/android/common/02}repository', {
    # The upstream XML declares schema 01 but includes the later vendor field.
    # Register its metadata using the schema supported by current SDK tools.
    'xmlns:sys-img': 'http://schemas.android.com/sdk/android/repo/sys-img2/05',
})
local_repo.append(copy.deepcopy(repository.find('license')))
local_package = ET.SubElement(local_repo, 'localPackage', {'path': PACKAGE, 'obsolete': 'false'})
for child in package:
    if child.tag not in {'archives', 'channelRef'}:
        node = copy.deepcopy(child)
        if child.tag == 'type-details':
            ET.SubElement(node, 'abis').text = 'arm64-v8a'
        local_package.append(node)
ET.ElementTree(local_repo).write(DESTINATION / 'package.xml', encoding='utf-8', xml_declaration=True)

# Make the official hardware profile available to avdmanager and Android Studio.
device_file = Path.home() / '.android/devices.xml'
if not device_file.exists():
    device_file.write_bytes((DESTINATION / 'devices.xml').read_bytes())

report = dict(package=PACKAGE, sdk_path=str(DESTINATION),
              source_url=package.findtext('./archives/archive/complete/url'),
              archive_bytes=expected_size, archive_sha1=expected_sha1,
              extraction='zero-filled regions preserved as sparse files',
              license_id=package.find('uses-license').get('ref'))
(PROJECT / '.dev/pleos-image-install.json').write_text(json.dumps(report, indent=2) + '\n')
print('Registered', DESTINATION, flush=True)
