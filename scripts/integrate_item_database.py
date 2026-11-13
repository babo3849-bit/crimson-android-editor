from pathlib import Path

# MainActivity contains the item database loader and parser.
# This build step only ensures network access is declared.
manifest = Path('app/src/main/AndroidManifest.xml')
if manifest.exists():
    text = manifest.read_text(encoding='utf-8')
    permission = '    <uses-permission android:name="android.permission.INTERNET" />'
    if 'android.permission.INTERNET' not in text:
        text = text.replace('<application', permission + '\\n    <application', 1)
        manifest.write_text(text, encoding='utf-8')
