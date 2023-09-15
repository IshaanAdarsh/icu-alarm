package hes.cs63.CEPMonitor;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Builder
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class HealthData {

    @JsonAlias({ "HR" })
    private String heartBeat;

    @JsonAlias({ "TEMP" })
    private String temp;

    @JsonAlias({ "SBP" })
    private String bp;

}
