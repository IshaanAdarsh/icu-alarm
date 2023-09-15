package hes.cs63.CEPMonitor;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.PatternSelectFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.streaming.connectors.kafka.config.StartupMode;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class StrokeRiskAlarm {

    private static KafkaSource<HealthData> kafkaSource;

    private static KafkaSource<HealthData> setKafkaSource() {
        String geCEP;
        List<String> streamList;

        geCEP = "geCEP";

        streamList = new ArrayList<>();
        streamList.add(geCEP);

        kafkaSource = KafkaSource.<String>builder()
                .setTopics(streamList)
                .setClientIdPrefix("geCEP")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setDeserializer(kafkaRecordDeserializationSchema)
                .setProperty("partition.discovery.interval.ms",
                        "300000")
                .setProperty("commit.offsets.on.checkpoint",
                        "true")
                .setProperty("register.consumer.metrics",
                        "true")
                .setProperties(getConsumerProps())
                .build();

        return kafkaSource;
    }

    public static final KafkaRecordDeserializationSchema kafkaRecordDeserializationSchema = new KafkaRecordDeserializationSchema<HealthData>() {
        private ObjectMapper mapper = new ObjectMapper();

        @Override
        public void deserialize(ConsumerRecord<byte[], byte[]> record, Collector<HealthData> out) throws IOException {
            try {
                HealthData eventDataMapping = mapper.readValue(record.value(), HealthData.class);

                out.collect(eventDataMapping);
            } catch (Exception ex) {
                System.out.println("Exception " + ex.getStackTrace());
            }
        }

        @Override
        public TypeInformation<HealthData> getProducedType() {
            return TypeInformation.of(HealthData.class);
        }
    };

    public static Properties getConsumerProps() {

        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("group.id", "test-consumer-group");
        props.put("enable.auto.commit", true);
        // props.put("security.protocol", "SASL_SSL");
        return props;
    }

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "localhost:9092");
        properties.setProperty("group.id", "test-consumer-group");

        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3,
                Time.of(60, TimeUnit.SECONDS)));

        // Use KafkaSource
        DataStream<HealthData> patientData = env
                .fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Kafka Source")
                .setParallelism(2)
                .uid("test");
        patientData.print();

        // DataStream<String> patientData = env.fromSource(
        // KafkaSource.<String>builder()
        // .setBootstrapServers("localhost:9092")
        // .setGroupId("test-consumer-group")
        // .setTopics(Arrays.asList("geCEP"))
        // .setDeserializer(KafkaRecordDeserializationSchema.valueOnly(StringDeserializer.class))
        // .setStartingOffsets(OffsetsInitializer.earliest())
        // .build(),
        // "Kafka Source");

        Pattern<String, ?> highRiskPattern = Pattern.<String>begin("first").where(new SimpleCondition<String>() {
            @Override
            public boolean filter(String value) {
                return getValue(value) > 3;
            }
        });

        PatternStream<String> patternStream = CEP.pattern(
                patientData,
                highRiskPattern);

        DataStream<String> strokeRiskAlerts = patternStream.select(new PatternSelectFunction<String, String>() {
            @Override
            public String select(Map<String, List<String>> pattern) throws Exception {
                String userId = pattern.get("first").get(0).split(",")[0].trim();
                int risk = getTotalRisk(pattern.get("first").get(0), pattern.get("middle").get(0),
                        pattern.get("last").get(0));
                return "Stroke Risk Alert: Patient ID - " + userId + ", Risk Level - " + risk;
            }
        });

        strokeRiskAlerts.print();

        env.execute("Stroke Risk Alert Job");

    }

    private static int getValue(String value) {
        // Parse the input value and extract the relevant data for risk calculation
        String[] parts = value.split(",");
        String type = parts[2].trim();
        double measurementValue = Double.parseDouble(parts[3].trim());

        // Implement your logic to check high stroke risk for different measurements
        if (type.equals("HR")) {
            // HeartMeasurement risk calculation logic
            int risk = 0;
            risk += measurementValue <= 50 ? 1 : 0;
            risk += measurementValue <= 40 ? 1 : 0;
            risk += measurementValue >= 91 ? 1 : 0;
            risk += measurementValue >= 110 ? 1 : 0;
            risk += measurementValue >= 131 ? 1 : 0;
            return risk;
        } else if (type.equals("SBP")) {
            // BloodPressureMeasurement risk calculation logic
            int risk = 0;
            risk += measurementValue <= 110 ? 1 : 0;
            risk += measurementValue <= 100 ? 1 : 0;
            risk += measurementValue <= 90 ? 1 : 0;
            risk += measurementValue >= 220 ? 3 : 0;
            return risk;
        } else if (type.equals("TEMP")) {
            // TempMeasurement risk calculation logic
            int risk = 0;
            risk += measurementValue <= 36 ? 1 : 0;
            risk += measurementValue <= 35 ? 2 : 0;
            risk += measurementValue >= 38.1 ? 1 : 0;
            risk += measurementValue >= 39.1 ? 1 : 0;
            return risk;
        }

        // Default risk calculation
        return 0;
    }

    private static int getTotalRisk(String firstValue, String middleValue, String lastValue) {
        // Calculate the total risk based on the values of the first, middle, and last
        // measurements
        int firstRisk = getValue(firstValue);
        int middleRisk = getValue(middleValue);
        int lastRisk = getValue(lastValue);

        return firstRisk + middleRisk + lastRisk;
    }
}