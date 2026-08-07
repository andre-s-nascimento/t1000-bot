#!/bin/bash

./gradlew clean build -x test    

export $(grep -v '^#' .env | xargs) && java -jar build/libs/tmill-0.0.1-SNAPSHOT.jar
