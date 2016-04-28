java -cp "protobuf-java-3.0.0-beta-2.jar;." ru.csc.roman_fedorov.Server
Timeout 5
for /l %x in (1, 1, 5) do (
   start cmd /C java -cp "protobuf-java-3.0.0-beta-2.jar;." ru.csc.roman_fedorov.RandomClient >> ClientLog.txt
)