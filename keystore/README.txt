Release signing keystore
========================

Files
-----
- evcharge-release.jks     — private signing key (do NOT share or commit)
- ../keystore.properties   — passwords + alias (do NOT commit)

Backup
------
Copy BOTH of these to a safe offline place (password manager + encrypted drive):

  keystore/evcharge-release.jks
  keystore.properties

If you lose the keystore or password, you cannot publish updates that install
over the same app ID. Users would have to uninstall first (and lose local history).

This key is for direct APK download (sideload), not Play App Signing.
