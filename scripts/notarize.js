'use strict';
const { execSync } = require('child_process');
const fs = require('fs');

exports.default = async function notarizeApp(context) {
  const { electronPlatformName, appOutDir } = context;
  if (electronPlatformName !== 'darwin') return;

  const appName = context.packager.appInfo.productFilename;
  const appPath = `${appOutDir}/${appName}.app`;
  const zipPath = `${appOutDir}/${appName}.zip`;

  console.log(`Zipping ${appPath}…`);
  execSync(`ditto -c -k --keepParent "${appPath}" "${zipPath}"`);

  console.log('Submitting to Apple notarization (no-wait)…');
  const raw = execSync(
    `xcrun notarytool submit "${zipPath}" \
      --apple-id "${process.env.APPLE_ID}" \
      --password "${process.env.APPLE_APP_SPECIFIC_PASSWORD}" \
      --team-id "${process.env.APPLE_TEAM_ID}" \
      --output-format json`,
    { encoding: 'utf8' }
  );

  fs.unlinkSync(zipPath);

  const parsed = JSON.parse(raw);
  console.log(`Submission ID: ${parsed.id}`);
  console.log(`Status: ${parsed.status}`);
  console.log('DMG will be uploaded unstapled — staple manually once Apple approves.');
  console.log(`Check status: xcrun notarytool info ${parsed.id} --apple-id $APPLE_ID --password $APPLE_APP_SPECIFIC_PASSWORD --team-id $APPLE_TEAM_ID`);
};
