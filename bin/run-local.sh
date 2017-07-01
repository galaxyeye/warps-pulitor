#!/bin/bash

# local mode, add class paths
if [ $PULITOR_RUNTIME_MODE == "LOCAL_DEVELOPMENT" ]; then
  # development mode
  for f in $PULITOR_HOME/$MODULE/lib/*.jar; do
    CLASSPATH=${CLASSPATH}:$f;
  done

  for f in $PULITOR_HOME/$MODULE/target/*.jar; do
    [[ ! sed-$f =~ -job.jar$ ]] && CLASSPATH=${CLASSPATH}:$f;
  done
fi

PID="$PULITOR_PID_DIR/pulitor-$PULITOR_IDENT_STRING-$COMMAND.pid"

if [ $COMMAND = "master" ]; then
  PULITOR_LOG_PREFIX=pulitor-$PULITOR_IDENT_STRING-$COMMAND-$HOSTNAME
else
  PULITOR_LOG_PREFIX="pulitor-$PULITOR_IDENT_STRING-all-$HOSTNAME"
fi

PULITOR_LOGFILE="$PULITOR_LOG_PREFIX.log"
PULITOR_LOGREPORT="$PULITOR_LOG_PREFIX.report.log"
PULITOR_LOGOUT="$PULITOR_LOG_PREFIX.out"

# local mode
loglog="${PULITOR_LOG_DIR}/${PULITOR_LOGFILE}"
logreport="${PULITOR_LOG_DIR}/${PULITOR_LOGREPORT}"
logout="${PULITOR_LOG_DIR}/${PULITOR_LOGOUT}"
logcmd="${PULITOR_TMP_DIR}/last-cmd-name"

PULITOR_OPTS=($PULITOR_OPTS -Dpulitor.log.dir=$PULITOR_LOG_DIR)
PULITOR_OPTS=("${PULITOR_OPTS[@]}" -Dpulitor.log.file=$PULITOR_LOGFILE)
PULITOR_OPTS=("${PULITOR_OPTS[@]}" -Dpulitor.home.dir=$PULITOR_HOME)
PULITOR_OPTS=("${PULITOR_OPTS[@]}" -Dpulitor.tmp.dir=$PULITOR_TMP_DIR)
PULITOR_OPTS=("${PULITOR_OPTS[@]}" -Dpulitor.id.str=$PULITOR_IDENT_STRING)
PULITOR_OPTS=("${PULITOR_OPTS[@]}" -Dpulitor.root.logger=${PULITOR_ROOT_LOGGER:-INFO,console})

export CLASSPATH
if [[ $VERBOSE_LEVEL == "1" ]]; then
  echo $CLASSPATH
fi

JAVA="$JAVA_HOME/bin/java"

# fix for the external Xerces lib issue with SAXParserFactory
# PULITOR_OPTS=(-Djavax.xml.parsers.DocumentBuilderFactory=com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl "${PULITOR_OPTS[@]}")
EXEC_CALL=("$JAVA" -Dproc_$COMMAND "-XX:OnOutOfMemoryError=\"kill -9 %p\"" $JAVA_HEAP_MAX "${PULITOR_OPTS[@]}")

MESSAGES=(
"============ Pulsar Runtime ================"
"\n`date`"
"\nCommand : $COMMAND"
"\nHostname : `hostname`"
"\nVersion : " `cat $PULITOR_HOME/VERSION`
"\nConfiguration directories : $PULITOR_CONF_DIR, $PULITOR_PRIME_CONF_DIR, $PULITOR_EXTRA_CONF_DIR"
"\nWorking direcotry : `pwd`"
"\nPulsar home : " $PULITOR_HOME
"\nLog file : $loglog" "\n"
"\nCommand Line : " ${EXEC_CALL[@]} $CLASS $@ "\n"
)

if [[ $VERBOSE_LEVEL == "1" ]]; then
  echo -e "${MESSAGES[@]}"
fi

echo $COMMAND > $logcmd

# run it
exec "${EXEC_CALL[@]}" $CLASS "$@" # > $logout 2>&1

echo $! > $PID
