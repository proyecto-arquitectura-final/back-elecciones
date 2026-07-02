package co.edu.elecciones.service;

import co.edu.elecciones.domain.Poll;
import co.edu.elecciones.domain.PollResult;
import co.edu.elecciones.dto.Requests.PollRequest;
import co.edu.elecciones.dto.Responses.PollResponse;
import co.edu.elecciones.dto.Responses.PollResultResponse;
import co.edu.elecciones.repository.CandidateRepository;
import co.edu.elecciones.repository.PollRepository;
import co.edu.elecciones.repository.PollResultRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PollService {
    private final PollRepository polls;
    private final PollResultRepository pollResults;
    private final CandidateRepository candidates;

    public PollService(PollRepository polls, PollResultRepository pollResults, CandidateRepository candidates) {
        this.polls = polls;
        this.pollResults = pollResults;
        this.candidates = candidates;
    }

    @Transactional(readOnly = true)
    public List<PollResponse> selectAll() {
        List<Poll> pollList = polls.selectAll();
        if (pollList.isEmpty()) {
            return List.of();
        }

        List<Long> pollIds = pollList.stream().map(poll -> poll.id).toList();
        Map<Long, List<PollResult>> byPoll = new LinkedHashMap<>();
        for (PollResult result : pollResults.selectByPollIds(pollIds)) {
            byPoll.computeIfAbsent(result.poll.id, ignored -> new ArrayList<>()).add(result);
        }

        return pollList.stream()
                .map(poll -> toResponse(poll, byPoll.getOrDefault(poll.id, Collections.emptyList())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PollResponse selectById(Long id) {
        Poll poll = polls.selectById(id).orElseThrow();
        return toResponse(poll, pollResults.selectByPollId(id));
    }

    @Transactional
    public PollResponse create(PollRequest request) {
        Poll poll = new Poll();
        apply(poll, request);
        Poll savedPoll = polls.save(poll);
        List<PollResult> savedResults = saveResults(savedPoll, request);
        return toResponse(savedPoll, savedResults);
    }

    @Transactional
    public PollResponse update(Long id, PollRequest request) {
        Poll poll = polls.selectById(id).orElseThrow();
        apply(poll, request);
        Poll savedPoll = polls.save(poll);
        pollResults.deleteByPollIdStatement(id);
        List<PollResult> savedResults = saveResults(savedPoll, request);
        return toResponse(savedPoll, savedResults);
    }

    @Transactional
    public void delete(Long id) {
        polls.selectById(id).orElseThrow();
        pollResults.deleteByPollIdStatement(id);
        polls.deleteByIdStatement(id);
    }

    @Transactional
    public int importCsv(MultipartFile file) throws Exception {
        int imported = 0;
        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .build()
                    .parse(reader);

            for (CSVRecord record : records) {
                Poll poll = new Poll();
                poll.source = record.get("source");
                poll.date = LocalDate.parse(record.get("date"));
                poll.sampleSize = Integer.parseInt(record.get("sampleSize"));
                poll.marginError = Double.parseDouble(record.get("marginError"));
                poll.methodology = record.get("methodology");
                polls.save(poll);
                imported++;
            }
        }
        return imported;
    }

    private void apply(Poll poll, PollRequest request) {
        poll.source = request.source();
        poll.date = request.date();
        poll.sampleSize = request.sampleSize();
        poll.marginError = request.marginError();
        poll.methodology = request.methodology();
    }

    private List<PollResult> saveResults(Poll poll, PollRequest request) {
        if (request.results() == null || request.results().isEmpty()) {
            return List.of();
        }

        List<PollResult> saved = new ArrayList<>();
        for (var item : request.results()) {
            PollResult result = new PollResult();
            result.poll = poll;
            result.candidate = candidates.selectById(item.candidateId()).orElseThrow();
            result.percentage = item.percentage();
            saved.add(pollResults.save(result));
        }
        return saved;
    }

    private PollResponse toResponse(Poll poll, List<PollResult> results) {
        List<PollResultResponse> responseResults = results.stream()
                .map(result -> new PollResultResponse(
                        result.id,
                        result.createdAt,
                        result.updatedAt,
                        result.candidate,
                        result.percentage
                ))
                .toList();

        return new PollResponse(
                poll.id,
                poll.createdAt,
                poll.updatedAt,
                poll.source,
                poll.date,
                poll.sampleSize,
                poll.marginError,
                poll.methodology,
                responseResults
        );
    }
}
