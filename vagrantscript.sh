#!/bin/bash
sudo add-apt-repository ppa:openjdk-r/ppa

# Add jdk repo

sudo add-apt-repository "deb https://apt.postgresql.org/pub/repos/apt/ trusty-pgdg main"
wget --quiet -O - https://postgresql.org/media/keys/ACCC4CF8.asc | sudo apt-key add -

# Add postgres repo

sudo apt-get update
sudo apt-get install -y git g++ automake libtool

sudo apt-get install -y postgresql postgresql-contrib

#Update, install postgres

sudo apt-get install -y openjdk-8-jre-headless

sudo /var/lib/dpkg/info/ca-certificates-java.postinst configure

curl https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein -o /usr/local/bin/lein
chmod a+x /usr/local/bin/lein

#Add jdk and lein

sudo -u postgres psql -c "CREATE DATABASE time_align_dev WITH ENCODING 'UTF8' TEMPLATE template0"
sudo -u postgres psql -c "CREATE DATABASE time_align_test ENCODING 'UTF8' TEMPLATE template0"
sudo -u postgres psql -c "CREATE USER dev WITH PASSWORD 'dev';"
sudo -u postgres psql -c "ALTER ROLE dev superuser;"
sudo -u postgres psql -c "CREATE USER test WITH PASSWORD 'test';"
sudo -u postgres psql -c "ALTER ROLE test superuser;"
sudo -u postgres psql -c "ALTER DATABASE time_align_dev OWNER TO dev;"
sudo -u postgres psql -c "ALTER DATABASE time_align_test OWNER TO test;"

# config postgres

sudo apt-get install build-essential chrpath libssl-dev libxft-dev libfreetype6-dev libfreetype6 libfontconfig1-dev libfontconfig1 -y
sudo wget https://bitbucket.org/ariya/phantomjs/downloads/phantomjs-2.1.1-linux-x86_64.tar.bz2
sudo tar xvjf phantomjs-2.1.1-linux-x86_64.tar.bz2 -C /usr/local/share/
sudo ln -s /usr/local/share/phantomjs-2.1.1-linux-x86_64/bin/phantomjs /usr/local/bin/
