# swarm-clj

A simple experiment on core.async and Redis pub/sub patterns. This starts a separate process for 3 test users:

echo user1 user2 user3 | xargs -P0 -n1 java -jar target/chat-clj-0.0.1-SNAPSHOT-standalone.jar 127.0.0.1:6379
