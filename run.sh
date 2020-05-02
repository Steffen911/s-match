#!/bin/bash

# Fetch dependencies and build project
# cd s-match
mvn clean package

DATA_PATH="/Users/steffen/OneDrive/UniMA/MasterThesis/Data"

# Convert txt hierarchies to xml input
mvn exec:java -Dexec.mainClass=it.unitn.disi.smatch.MatchManager -Dexec.args="convert $DATA_PATH/taxo_l.txt $DATA_PATH/taxo_l.xml -config=conf/s-match-Tab2XML.properties"
mvn exec:java -Dexec.mainClass=it.unitn.disi.smatch.MatchManager -Dexec.args="convert $DATA_PATH/taxo_r.txt $DATA_PATH/taxo_r.xml -config=conf/s-match-Tab2XML.properties"

# Prepare taxonomies for matching
mvn exec:java -Dexec.mainClass=it.unitn.disi.smatch.MatchManager -Dexec.args="offline $DATA_PATH/taxo_l.xml $DATA_PATH/taxo_l.xml"
mvn exec:java -Dexec.mainClass=it.unitn.disi.smatch.MatchManager -Dexec.args="offline $DATA_PATH/taxo_r.xml $DATA_PATH/taxo_r.xml"

# Perform the actual matching
mvn exec:java -Dexec.mainClass=it.unitn.disi.smatch.MatchManager -Dexec.args="online $DATA_PATH/taxo_l.xml $DATA_PATH/taxo_r.xml $DATA_PATH/result.txt"
