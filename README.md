drain
=====

drain recieves and acts upon application logs from heroku (it is added as a log drain for the microryza rails app)

At present it:
* publishes hits to redis (consumed by Microryza/live)
* pushes dyno memory usage stats to librato (https://metrics.librato.com/metrics/dyno.memory_total)
* pushes postgres usage stats to librato (https://metrics.librato.com/metrics/postgres)
* annotates deploys in librato


