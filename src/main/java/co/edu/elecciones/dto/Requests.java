package co.edu.elecciones.dto;

import co.edu.elecciones.domain.*;

import java.time.LocalDate;
import java.util.*;

public class Requests {
    public record UserRequest(String name, String email, String password, Role role, Boolean active) {
    }

    public record PartyRequest(String name, String acronym, String color, Integer foundationYear, Boolean active) {
    }

    public record CandidateRequest(String name, String vicePresidentName, Long partyId, ElectionType electionType,
                                   String department, String municipality, Boolean active) {
    }

    public record ElectionRequest(String name, ElectionType type, ElectionRound round, LocalDate electionDate,
                                  ElectionState state) {
    }

    public record PollResultRequest(Long candidateId, Double percentage) {
    }

    public record PollRequest(String source, LocalDate date, Integer sampleSize, Double marginError, String methodology,
                              List<PollResultRequest> results) {
    }

    public record OfficialResultRequest(Long electionId, Long candidateId, String department, String municipality,
                                        Long votes, Double percentage, Integer reportedTables, Integer totalTables,
                                        Double participation, String source) {
    }

    public record ChatRequest(String question) {
    }

    public record McpInvokeRequest(String tool, Map<String, Object> arguments) {
    }
}
