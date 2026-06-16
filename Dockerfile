FROM public.ecr.aws/lambda/java:25

COPY target/card-transaction-system-*.jar ${LAMBDA_TASK_ROOT}/app.jar

ENV SPRING_CLOUD_FUNCTION_DEFINITION=processTransactionFunction
ENV SPRING_PROFILES_ACTIVE=!local

CMD ["org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"]
