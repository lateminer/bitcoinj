Use jdk1.8.0_25 and apache-maven-3.2.3

skip tests

mvn clean install -Dmaven.test.skip=true

cd examples

unzip chain

Chain is
http://www.h2database.com/html/mvstore.html

mvn exec:java -Dexec.mainClass=org.bitcoinj.examples.ForwardingService -Dexec.args="BMmje2XuDzgcsFbRisuiRYR7qGTa21fo84" -Dmaven.test.skip=true --log-file blck.log

observe blck.log located in examples

Happy hacking ;)

Donations: BMmje2XuDzgcsFbRisuiRYR7qGTa21fo84
