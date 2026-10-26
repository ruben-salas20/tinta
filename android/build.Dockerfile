# Imagen para compilar la app sin instalar el SDK de Android en la máquina.
# La construye y la usa compilar.sh.
FROM eclipse-temurin:21-jdk
RUN apt-get update && apt-get install -y --no-install-recommends unzip curl && rm -rf /var/lib/apt/lists/*
ENV ANDROID_HOME=/opt/android-sdk
RUN mkdir -p $ANDROID_HOME/cmdline-tools \
    && curl -fsSL -o /tmp/ct.zip https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip \
    && unzip -q /tmp/ct.zip -d /tmp/ct && mv /tmp/ct/cmdline-tools $ANDROID_HOME/cmdline-tools/latest && rm -rf /tmp/ct /tmp/ct.zip \
    && yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null \
    && $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-37.0" "build-tools;37.0.0" "platform-tools" > /dev/null \
    && chmod -R a+rX $ANDROID_HOME
ENV PATH=$PATH:$ANDROID_HOME/build-tools/37.0.0
