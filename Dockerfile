FROM eclipse-temurin:8-jdk

RUN apt-get update && apt-get install -y --no-install-recommends \
    wget unzip curl && rm -rf /var/lib/apt/lists/*

# Install Gradle 5.4.1 manually
RUN wget -q https://services.gradle.org/distributions/gradle-5.4.1-bin.zip -O /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && rm /tmp/gradle.zip

ENV GRADLE_HOME=/opt/gradle-5.4.1
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH="${GRADLE_HOME}/bin:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${PATH}"

# Install Android SDK Command Line Tools
RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-8512546_latest.zip -O /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools \
    && mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest \
    && rm /tmp/cmdline-tools.zip

# Accept licenses and install platform 19
RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager "platforms;android-19" "platforms;android-28" "build-tools;28.0.3" "platform-tools"

WORKDIR /app
