#!/usr/bin/env bash

function __bin_pulitor {
  # run $bin/pulitor, exit if exit value indicates error

  echo "$PULITOR $@" ; # echo command and arguments
  "$PULITOR" "$@"

  RETCODE=$?
  if [ $RETCODE -ne 0 ]
  then
      echo "Error running:"
      echo "  $PULITOR $@"
      echo "Failed with exit value $RETCODE."
      exit $RETCODE
  fi
}

function __is_crawl_loop_stopped {
  STOP=0
  if [ -e ".STOP" ] || [ -e ".KEEP_STOP" ]; then
   if [ -e ".STOP" ]; then
     mv .STOP ".STOP_EXECUTED_`date +%Y%m%d.%H%M%S`"
   fi

    STOP=1
  fi

  (( $STOP==1 )) && return 0 || return 1
}

function __check_index_server_available {
  for i in {1..200}
  do
    wget -q --spider $SOLR_TEST_URL

    if (( $?==0 )); then
      return 0;
    fi

    if ( __is_crawl_loop_stopped ); then
      echo "STOP file found - escaping loop"
      exit 0
    fi

    echo "Index server not available, check 15s later ..."
    sleep 15s
  done

  echo "Index server is gone."
  return 1
}

