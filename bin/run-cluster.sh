#!/bin/bash

bin=`dirname "$0"`
bin=`cd "$bin">/dev/null; pwd`

echo "CLUSTER MODE"
# check that hadoop can be found on the path
if [ $(which hadoop | wc -l ) -eq 0 ]; then
  echo "Can't find Hadoop executable. Add HADOOP_HOME/bin to the path or run in local mode."
  exit -1;
fi

for f in "${PULITOR_HOME}"/pulitor-*-job.jar; do
  if [ -f $f ]; then
    PULITOR_JOB=$f
  fi
done

# distributed mode
EXEC_CALL=(hadoop jar "$PULITOR_JOB")
echo "${EXEC_CALL[@]}" $CLASS "$@"
exec "${EXEC_CALL[@]}" $CLASS "$@"
