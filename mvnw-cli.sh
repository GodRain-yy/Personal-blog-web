#!/usr/bin/env bash
# 便捷构建脚本（离线模式，Windows 路径写法）
export PATH="/usr/bin:/bin:/usr/local/bin:/c/Windows/System32:$PATH"
M2=D:/apache-maven-3.9.16/apache-maven-3.9.16
REPO=C:/Users/18010/.m2/repository
cd D:/bokersystem_web || exit 1
"$JAVA_HOME/bin/java.exe" \
  -Dmaven.multiModuleProjectDirectory=D:/bokersystem_web \
  -classpath "$M2/boot/plexus-classworlds-2.11.0.jar" \
  -Dclassworlds.conf="$M2/bin/m2.conf" \
  -Dmaven.home="$M2" \
  -Dlibrary.jansi.path="$M2/lib/jansi-native" \
  -Dmaven.repo.local="$REPO" \
  org.codehaus.plexus.classworlds.launcher.Launcher -B -o "$@"
