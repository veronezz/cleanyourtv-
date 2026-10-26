#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
key_path="${1:-${UNITV_LAUNCHER_KEY:-$project_dir/signing/debug.keystore}}"
if (( $# > 0 )); then shift; fi
if [[ ! -f "$key_path" ]]; then echo 'Informe o caminho da sua debug.keystore original como primeiro argumento.' >&2; exit 1; fi
export UNITV_LAUNCHER_KEY="$(realpath "$key_path")"
certificate_file="$(mktemp)"
trap 'rm -f "$certificate_file"' EXIT
if ! keytool -exportcert -keystore "$UNITV_LAUNCHER_KEY" -storepass android -alias androiddebugkey -file "$certificate_file" >/dev/null 2>&1; then
  echo 'Nao foi possivel ler o certificado da chave debug original.' >&2; exit 1
fi
actual_cert="$(sha256sum "$certificate_file" | cut -d ' ' -f 1)"
expected_cert="$(tr -d '[:space:]' < "$project_dir/signing/expected-certificate.sha256")"
if [[ "$actual_cert" != "$expected_cert" ]]; then echo 'Certificado diferente do launcher/ponte UniTV. Use a chave original.' >&2; exit 1; fi
chmod +x "$project_dir/gradlew"
"$project_dir/gradlew" -p "$project_dir" :app:assembleRelease :app:testReleaseUnitTest --no-daemon "$@"
mkdir -p "$project_dir/dist"
cp "$project_dir/app/build/outputs/apk/release/app-release.apk" "$project_dir/dist/CleanYourTV.apk"
echo "APK: $project_dir/dist/CleanYourTV.apk"
