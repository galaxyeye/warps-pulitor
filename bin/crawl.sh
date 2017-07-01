#!/usr/bin/env bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# 
# The Crawl command script : crawl <seedDir> <crawlId> <solrURL> <numberOfRounds>
#
# 
# UNLIKE THE PULITOR ALL-IN-ONE-CRAWL COMMAND THIS SCRIPT DOES THE LINK INVERSION AND
# INDEXING FOR EACH BATCH

bin="`dirname "$0"`"
bin="`cd "$bin"; pwd`"

 . "$bin"/pulitor-config.sh
 . "$bin"/pulitor-common.sh

#############################################
# MODIFY THE PARAMETERS BELOW TO YOUR NEEDS #
#############################################
# set max depth, no more links are collected in deeper pages
maxDepth=1
minAnchorLen=5
maxAnchorLen=40
maxOutlinks=500

# set the number of slaves nodes
numSlaves=1
if [ -n "$NUMBER_SLAVES" ]; then
 numSlaves=$NUMBER_SLAVES
fi

# and the total number of available tasks
# sets Hadoop parameter "mapreduce.job.reduces"
numTasks=`expr $numSlaves \* 2`

# number of urls to fetch in one iteration
# It's depend on how fast do you want to finish the fetch loop
batchSize=`expr $numTasks \* 1000`
if [ -n "$BATCH_SIZE" ]; then
 batchSize=$BATCH_SIZE
fi

#############################################

function printUsage() {
  echo "Usage: crawl.sh [options...] <seeds> <crawlID> <numberOfRounds>"

  echo
  echo "Options: "
  echo "  -v, --verbose    Talk more"
  echo "  -h, --help       The help text"
  exit 1
}

while [ $# -gt 0 ]
do
case $1 in
    -v|--verbose)
        export VERBOSE_LEVEL=1
        shift
        ;;
    -h|--help)
        SHOW_HELP=true
        shift
        ;;
    -*)
        echo "Unrecognized option : $1"
        echo "Try 'pulitor --help' for more information."
        exit 0
        ;;
    *)
        break
        ;;
esac
done

if [[ $SHOW_HELP ]]; then
  printUsage
  exit 0
fi

if [ "$#" -lt 3 ]; then
  printUsage
  exit 1
fi

SEEDS="$1"
shift
CRAWL_ID="$1"
shift
LIMIT="$1"
shift

if [ "$#" -gt 1 ]; then
  printUsage
  exit 1
fi

if [ "$PULITOR" = "" ]; then
  export PULITOR="$bin/pulitor"
fi

if [ "$VERBOSE_LEVEL" == "1" ]; then
  PULITOR_SCRIPT_OPTIONS=(${PULITOR_SCRIPT_OPTIONS[@]} --verbose)
fi

# note that some of the options listed here could be set in the
# corresponding hadoop site xml param file
COMMAND_OPTIONS=(
    "-D mapreduce.job.reduces=$numTasks" # TODO : not suitable for all jobs
    "-D crawl.max.distance=$maxDepth"
    "-D parse.max.outlinks=$maxOutlinks"
    "-D parse.min.anchor.length=$minAnchorLen"
    "-D parse.max.anchor.length=$maxAnchorLen"
    "-D mapred.child.java.opts=-Xmx1000m"
    "-D mapreduce.reduce.speculative=false"
    "-D mapreduce.map.speculative=false"
    "-D mapreduce.map.output.compress=true"
)

if [ ! -e "$PULITOR_HOME/logs" ]; then
  mkdir "$PULITOR_HOME/logs"
fi

# determines whether mode based on presence of job file
if [ ${PULITOR_RUNTIME_MODE} == "DISTRIBUTE" ]; then
  # check that hadoop can be found on the path
  if [ $(which hadoop | wc -l ) -eq 0 ]; then
    echo "Can't find Hadoop executable. Add HADOOP_HOME/bin to the path or run in local mode."
    exit -1;
  fi
fi

# initial injection
# echo "Injecting seed URLs"
if [[ $SEEDS != "false" ]]; then
  __bin_pulitor "${PULITOR_SCRIPT_OPTIONS[@]}" inject "$SEEDS" -crawlId "$CRAWL_ID"
fi

# main loop : rounds of generate - fetch - parse - update
for ((a=1; a <= $LIMIT ; a++))
do
  if ( __is_crawl_loop_stopped ); then
     echo "STOP file found - escaping loop"
     exit 0
  fi

  if ( ! __check_index_server_available ); then
    exit 1
  fi

  echo -e "\n\n\n"
  echo `date` ": Iteration $a of $LIMIT"

  batchId=`date +%s`-$RANDOM

  echo "Generating : "
  __bin_pulitor "${PULITOR_SCRIPT_OPTIONS[@]}" generate "${COMMAND_OPTIONS[@]}" -batchId "$batchId" -topN ${batchSize} -crawlId "$CRAWL_ID"

  __bin_pulitor "${PULITOR_SCRIPT_OPTIONS[@]}" fetch "${COMMAND_OPTIONS[@]}" -batchId "$batchId" -crawlId "$CRAWL_ID"

done

exit 0
