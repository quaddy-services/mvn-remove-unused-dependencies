echo on

rem Install
rem ftp://ftp.gnupg.org/gcrypt/binary/gnupg-w32cli-1.4.11.exe
rem gpg --gen-key
rem gpg --keyserver hkp://pool.sks-keyservers.net --send-keys C6EED57A
rem see http://www.sonatype.com/people/2010/01/how-to-generate-pgp-signatures-with-maven/
rem gpg --list-keys
call mvn clean deploy -Dgpg.passphrase=%quaddypassphrase% -P sonatype-oss-release

echo on 
rem for relase behind a proxy:
rem $ git config --global http.proxy http://proxyuser:proxypwd@proxy.server.com:8080

rem mvn release:prepare release:perform -Dresume=false -Darguments=-Dgpg.passphrase=%quaddypassphrase% -P sonatype-oss-release
