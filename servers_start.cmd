@echo off
 
call servers_stop.cmd

del YonaDB.*
 
start "HSQL database" java -cp "%HSQLDB_HOME%/lib/sqltool.jar" org.hsqldb.Server -database.0 file:YonaDB -dbname.0 xdb
start "Admin service" cmd /c gradlew %1 :adminservice:run
start "Analysis service" cmd /c gradlew %1 :analysisservice:run
start "App service" cmd /c gradlew %1 :appservice:run

set GRADLE_OPTS=
echo Wait until all services are started.
pause
cmd /c gradlew --rerun-tasks :adminservice:intTest :analysisservice:intTest :appservice:intTest 

