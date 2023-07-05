FROM ubuntu:latest
RUN apt-get update
RUN apt-get install --no-install-recommends -y net-tools openjdk-17-jdk iputils-*

RUN apt-get install -y locales && locale-gen pt_BR.UTF-8

ENV PATH="/root/.local/bin:${PATH}:/SDT3"

RUN apt install -y nano
