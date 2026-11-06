#!/usr/bin/env python3
"""Dependency-free fallback build using JDK 17 and official Android SDK tools.

ANDROID_HOME must contain platforms/android-35 and build-tools/35.0.0.
Produces an unsigned, zipaligned APK; sign it with your own release key.
"""
import os,pathlib,subprocess,zipfile,shutil
root=pathlib.Path(__file__).resolve().parents[1]
sdk=pathlib.Path(os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT','')))
if not str(sdk) or not (sdk/'platforms/android-35/android.jar').exists():raise SystemExit('Set ANDROID_HOME to an Android SDK with platform 35 and build-tools 35.0.0')
tools=sdk/'build-tools/35.0.0'
jdk=pathlib.Path(os.environ['JAVA_HOME'])/'bin' if 'JAVA_HOME' in os.environ else pathlib.Path('/usr/bin')
build=root/'app/build/sdk';shutil.rmtree(build,ignore_errors=True)
for d in ['classes','generated','dex']:(build/d).mkdir(parents=True,exist_ok=True)
def run(args):subprocess.run([str(a) for a in args],check=True)
manifest=(root/'app/src/main/AndroidManifest.xml').read_text().replace('<manifest ','<manifest package="org.grimmoir.lunera.lite" ',1)
(build/'AndroidManifest.xml').write_text(manifest)
run([tools/'aapt2','compile','--dir',root/'app/src/main/res','-o',build/'resources.zip'])
run([tools/'aapt2','link','-o',build/'resources.apk','--manifest',build/'AndroidManifest.xml','-I',sdk/'platforms/android-35/android.jar','--java',build/'generated','--min-sdk-version','23','--target-sdk-version','35','--version-code','10000','--version-name','1.0.0',build/'resources.zip'])
sources=list((root/'app/src/main/java').rglob('*.java'))+list((build/'generated').rglob('*.java'))
run([jdk/'javac','-source','8','-target','8','-encoding','UTF-8','-classpath',sdk/'platforms/android-35/android.jar','-d',build/'classes']+sources)
run([jdk/'jar','cf',build/'classes.jar','-C',build/'classes','.'])
run([jdk/'java','-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--release','--min-api','23','--lib',sdk/'platforms/android-35/android.jar','--output',build/'dex',build/'classes.jar'])
shutil.copyfile(build/'resources.apk',build/'unaligned.apk')
with zipfile.ZipFile(build/'unaligned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
 for p in (build/'dex').glob('*.dex'):z.write(p,p.name)
run([tools/'zipalign','-f','-p','4',build/'unaligned.apk',build/'Lunera-Lite-1.0.0-unsigned.apk'])
print('Built:',build/'Lunera-Lite-1.0.0-unsigned.apk')
