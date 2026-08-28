#/usr/bin/bash

debInst() {
    dpkg-query -Wf'${db:Status-abbrev}' "$1" 2>/dev/null | grep -q '^i'
}

# we need the JDK (not JRE) to build the plugin
if ! debInst "openjdk-21-jdk"; then
  echo -e "Missing package.\nPlease install: 'sudo apt update && sudo apt install openjdk-21-jdk'"
  exit 1
fi

export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64/
atlas-package
