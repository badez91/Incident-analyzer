@echo off
set JAVA_HOME=C:\Users\faiz\.jdks\temurin-17.0.16
"C:\Users\faiz\Downloads\apache-maven-3.2.5-bin\apache-maven-3.2.5\bin\mvn.bat" clean package -s settings-override.xml -DskipTests
pause
