FROM eclipse-temurin:25

ENV ANDROID_HOME=/opt/android-sdk
ENV JAVA_OPTS=--enable-native-access=ALL-UNNAMED

RUN apt-get update && apt-get install -y --no-install-recommends wget unzip rsync && \
    rm -rf /var/lib/apt/lists/*

RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

RUN yes | ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null && \
    ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager "platforms;android-37.0" "build-tools;37.0.0"

WORKDIR /build

COPY entrypoint.sh /usr/local/bin/android-build
ENTRYPOINT ["sh", "/usr/local/bin/android-build"]
