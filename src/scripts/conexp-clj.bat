@echo off

set CONEXP_CLJ_HOME=..
set CONEXP_CLJ_JARS=.
set CONEXP_CLJ_JARS=%CONEXP_CLJ_JARS%;%CONEXP_CLJ_HOME%/lib/clojure.jar
set CONEXP_CLJ_JARS=%CONEXP_CLJ_JARS%;%CONEXP_CLJ_HOME%/lib/clojure-contrib.jar
set CONEXP_CLJ_JARS=%CONEXP_CLJ_JARS%;%CONEXP_CLJ_HOME%/lib/jline.jar
set CONEXP_CLJ_JARS=%CONEXP_CLJ_JARS%;%CONEXP_CLJ_HOME%/lib/G.jar
set CONEXP_CLJ_JARS=%CONEXP_CLJ_JARS%;%CONEXP_CLJ_HOME%/lib/conexp-clj.jar
set CONEXP_CLJ_CONSOLE_INIT=%CONEXP_CLJ_HOME%/lib/conexp-clj-console-init.clj

java -cp %CONEXP_CLJ_JARS% jline.ConsoleRunner clojure.lang.Repl %CONEXP_CLJ_CONSOLE_INIT%