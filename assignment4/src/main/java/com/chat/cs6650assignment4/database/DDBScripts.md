We need to create a dynamo db table with proper indexes for this assignment.

1. You can use below commands to create the ddb table and the indexes

```
aws dynamodb create-table \
    --table-name ChatMessages \
    --attribute-definitions \
        AttributeName=roomId,AttributeType=S \
        AttributeName=timestampSk,AttributeType=S \
        AttributeName=userId,AttributeType=S \
    --key-schema \
        AttributeName=roomId,KeyType=HASH \
        AttributeName=timestampSk,KeyType=RANGE \
    --provisioned-throughput \
        ReadCapacityUnits=50,WriteCapacityUnits=50 \
    --global-secondary-indexes \
        "[{\"IndexName\": \"UserIndex\", \
        \"KeySchema\":[{\"AttributeName\":\"userId\",\"KeyType\":\"HASH\"},{\"AttributeName\":\"timestampSk\",\"KeyType\":\"RANGE\"}], \
        \"Projection\":{\"ProjectionType\":\"ALL\"}, \
        \"ProvisionedThroughput\":{\"ReadCapacityUnits\":10,\"WriteCapacityUnits\":50}}]" \
    --region us-east-1
```

2. Change the table to on-demand

3. Use below command to create the timeIndex GSI

```
aws dynamodb update-table \
    --table-name ChatMessages \
    --attribute-definitions AttributeName=bucketId,AttributeType=S AttributeName=timestampSk,AttributeType=S \
    --global-secondary-index-updates \
    "[{\"Create\":{\"IndexName\": \"TimeIndex\",\"KeySchema\":[{\"AttributeName\":\"bucketId\",\"KeyType\":\"HASH\"},{\"AttributeName\":\"timestampSk\",\"KeyType\":\"RANGE\"}],\"Projection\":{\"ProjectionType\":\"ALL\"}}}]" \
    --region us-east-1
```