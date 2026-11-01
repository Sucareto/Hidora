#!/bin/sh
set -eu

usage() {
  cat <<'USAGE'
Usage: ./linux-build.sh | windows-build.bat [options]
   or: docker compose run --rm --build apk [options]

Builds the release APK inside Docker. The APK and the R8 mapping
(mapping.txt, for retracing obfuscated stack traces) are written to output/.

  --debug            also build the debug APK
  --lint             also run Android Lint on the release variant
                     (report: output/lint-results-release.txt)
  --keystore FILE    sign the release APK with this keystore (requires --alias);
                     a relative path is resolved inside the project directory;
                     without it the release APK is signed with a test key
  --alias NAME       key alias inside the keystore
  --update-verification
                     regenerate the dependency checksums (implies --debug and
                     --lint) with an empty Gradle home, downloading everything
                     again; the new file is written to
                     output/verification-metadata.xml and must be copied to
                     gradle/ by hand
  -h, --help         show this help

Passwords for --keystore are read from KEYSTORE_PASSWORD / KEY_PASSWORD,
or asked interactively when those are not set.
The test key and the debug key are kept in the Docker volume mounted at /keys:
created on first use, reused afterwards.
USAGE
}

if [ -t 1 ]; then
  step() { printf '\033[1;36m==>\033[0m \033[1m%s\033[0m\n' "$1"; }
else
  step() { printf '==> %s\n' "$1"; }
fi

fail() {
  echo "$1" >&2
  exit "${2:-1}"
}

read_secret() {
  [ -t 0 ] || fail "$1 is not set and no terminal is available to ask for it."
  printf '%s: ' "$2" >&2
  stty -echo
  read -r value
  stty echo
  echo >&2
  printf '%s' "$value"
}

new_key() {
  keytool -genkeypair -keystore "$1" -storetype PKCS12 -alias "$2" -storepass "$3" -keypass "$3" \
    -keyalg RSA -keysize 2048 -validity 10950 -dname "$4" >/dev/null 2>&1 || fail "Could not create $1"
}

[ -n "${APP_NAME:-}" ] || fail "APP_NAME is not set (see compose.yaml)."
APP_SLUG=$(printf '%s' "$APP_NAME" | tr 'A-Z' 'a-z')

KEYS_DIR=/keys
DEBUG_KEYSTORE=$KEYS_DIR/debug.keystore
TEST_KEYSTORE=$KEYS_DIR/release-test.jks
TEST_ALIAS=$APP_SLUG-test
TEST_PASSWORD=$APP_SLUG-test

debug=0; lint=0; verify=0; keystore=""; alias=""
while [ $# -gt 0 ]; do
  case $1 in
    --debug) debug=1 ;;
    --lint) lint=1 ;;
    --keystore) [ $# -ge 2 ] || fail "--keystore needs a file" 2; keystore=$2; shift ;;
    --alias) [ $# -ge 2 ] || fail "--alias needs a name" 2; alias=$2; shift ;;
    --update-verification) verify=1; debug=1; lint=1 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; fail "Unknown option: $1" 2 ;;
  esac
  shift
done

tasks="assembleRelease"
[ $debug = 1 ] && tasks="assembleDebug $tasks"
[ $lint = 1 ] && tasks="$tasks lintRelease"
[ $verify = 1 ] && tasks="--write-verification-metadata sha256 --no-build-cache --no-configuration-cache $tasks"

if [ $debug = 1 ]; then
  if [ ! -f "$DEBUG_KEYSTORE" ]; then
    step "Creating debug key in the keys volume"
    new_key "$DEBUG_KEYSTORE" androiddebugkey android "C=US, O=Android, CN=Android Debug"
  fi
  mkdir -p /root/.android
  cp -f "$DEBUG_KEYSTORE" /root/.android/debug.keystore
fi

if [ -n "$keystore" ]; then
  [ -n "$alias" ] || fail "--keystore requires --alias." 2
  case $keystore in /*) ;; *) keystore=/src/$keystore ;; esac
  [ -f "$keystore" ] || fail "Keystore not found: $keystore (it must be inside the project directory)"
  if [ -z "${KEYSTORE_PASSWORD:-}" ]; then
    KEYSTORE_PASSWORD=$(read_secret KEYSTORE_PASSWORD "Keystore password")
  fi
  if [ -z "${KEY_PASSWORD:-}" ]; then
    KEY_PASSWORD=$(read_secret KEY_PASSWORD "Key password (empty = same as keystore)")
    KEY_PASSWORD=${KEY_PASSWORD:-$KEYSTORE_PASSWORD}
  fi
  KEYSTORE_PATH=$keystore
  KEY_ALIAS=$alias
  step "Release will be signed with key '$alias'"
else
  if [ ! -f "$TEST_KEYSTORE" ]; then
    step "Creating test release key in the keys volume"
    new_key "$TEST_KEYSTORE" "$TEST_ALIAS" "$TEST_PASSWORD" "CN=$APP_NAME Test"
  fi
  KEYSTORE_PATH=$TEST_KEYSTORE
  KEYSTORE_PASSWORD=$TEST_PASSWORD
  KEY_ALIAS=$TEST_ALIAS
  KEY_PASSWORD=$TEST_PASSWORD
  step "Release will be signed with the test key"
fi
export KEYSTORE_PATH KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD

step "Copying sources"
rsync -a --exclude=.git --filter=':- .gitignore' /src/ /build/
cd /build
if [ $verify = 1 ]; then
  rm -f gradle/verification-metadata.xml
  export GRADLE_USER_HOME=/tmp/gradle-verification
  step "Using an empty Gradle home so every artifact is downloaded and recorded"
fi
rm -f /output/*.apk /output/mapping.txt /output/lint-results-release.txt /output/verification-metadata.xml

step "Building: $tasks"
status=0
sh ./gradlew $tasks || status=$?

step "Copying outputs to output/"
for apk in app/build/outputs/apk/debug/*.apk app/build/outputs/apk/release/*.apk; do
  [ -f "$apk" ] && cp -f "$apk" /output/
done
mapping=app/build/outputs/mapping/release/mapping.txt
[ -f "$mapping" ] && cp -f "$mapping" /output/
report=app/build/reports/lint-results-release.txt
[ -f "$report" ] && cp -f "$report" /output/
[ $verify = 1 ] && [ -f gradle/verification-metadata.xml ] && cp -f gradle/verification-metadata.xml /output/
owner=$(stat -c %u:%g /src)
[ "$owner" = "0:0" ] || chown -R "$owner" /output

ls -l /output
[ -f "$report" ] && echo "Lint: $(tail -1 "$report")"
exit $status
