package co.edu.elecciones.service;

import co.edu.elecciones.config.BusinessConflictException;
import co.edu.elecciones.domain.Candidate;
import co.edu.elecciones.domain.Election;
import co.edu.elecciones.domain.ElectionState;
import co.edu.elecciones.domain.ElectionType;
import co.edu.elecciones.domain.Party;
import co.edu.elecciones.dto.Requests.CandidateRequest;
import co.edu.elecciones.dto.Responses.CandidateCounters;
import co.edu.elecciones.dto.Responses.CandidateElection;
import co.edu.elecciones.dto.Responses.CandidateManagement;
import co.edu.elecciones.dto.Responses.CandidateParty;
import co.edu.elecciones.dto.Responses.CandidateResponse;
import co.edu.elecciones.repository.CandidateRepository;
import co.edu.elecciones.repository.ElectionRepository;
import co.edu.elecciones.repository.PartyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class CandidateManagementService {
    private final CandidateRepository candidates;
    private final PartyRepository parties;
    private final ElectionRepository elections;

    public CandidateManagementService(
            CandidateRepository candidates,
            PartyRepository parties,
            ElectionRepository elections
    ) {
        this.candidates = candidates;
        this.parties = parties;
        this.elections = elections;
    }

    @Transactional(readOnly = true)
    public CandidateManagement getManagement() {
        List<CandidateResponse> candidateRows = candidates.selectManagementRows().stream()
                .map(this::toResponse)
                .toList();

        long total = candidateRows.size();
        long active = candidateRows.stream().filter(CandidateResponse::active).count();
        long presidency = candidateRows.stream().filter(item -> "PRESIDENCIA".equals(item.electionType())).count();
        long senate = candidateRows.stream().filter(item -> "SENADO".equals(item.electionType())).count();
        long chamber = candidateRows.stream().filter(item -> "CAMARA".equals(item.electionType())).count();
        long representedParties = candidateRows.stream()
                .map(item -> item.party().id())
                .distinct()
                .count();

        CandidateCounters counters = new CandidateCounters(
                total,
                active,
                total - active,
                presidency,
                senate,
                chamber,
                representedParties
        );

        List<CandidateParty> partyOptions = parties.selectAll().stream()
                .map(this::toParty)
                .toList();

        List<CandidateElection> electionOptions = elections.selectAll().stream()
                .map(this::toElection)
                .toList();

        return new CandidateManagement(
                counters,
                candidateRows,
                partyOptions,
                electionOptions,
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public List<CandidateResponse> selectAll() {
        return candidates.selectManagementRows().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CandidateResponse selectById(Long id) {
        Candidate candidate = candidates.selectById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe el candidato solicitado"));
        return toResponse(candidate);
    }

    @Transactional
    public CandidateResponse create(CandidateRequest request) {
        Candidate candidate = new Candidate();
        apply(candidate, request, null);
        Candidate saved = candidates.save(candidate);
        return toResponse(saved);
    }

    @Transactional
    public CandidateResponse update(Long id, CandidateRequest request) {
        Candidate candidate = candidates.selectById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe el candidato solicitado"));
        apply(candidate, request, id);
        Candidate saved = candidates.save(candidate);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Candidate candidate = candidates.selectById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe el candidato solicitado"));

        long officialResults = candidates.selectOfficialResultCount(id);
        long pollResults = candidates.selectPollResultCount(id);
        if (officialResults > 0 || pollResults > 0) {
            throw new BusinessConflictException(
                    "No se puede eliminar a " + candidate.name
                            + " porque tiene información electoral asociada. "
                            + "Puedes marcarlo como inactivo."
            );
        }

        candidates.deleteByIdStatement(id);
    }

    private void apply(Candidate candidate, CandidateRequest request, Long currentId) {
        String name = normalizeRequired(request.name(), "El nombre del candidato es obligatorio");
        Party party = parties.selectById(request.partyId())
                .orElseThrow(() -> new IllegalArgumentException("El partido seleccionado no existe"));
        Election election = elections.selectById(request.electionId())
                .orElseThrow(() -> new IllegalArgumentException("La elección seleccionada no existe"));

        if (!party.active) {
            throw new IllegalArgumentException("El partido seleccionado está inactivo");
        }
        if (election.state == ElectionState.ARCHIVADA) {
            throw new IllegalArgumentException("No se pueden registrar candidatos en una elección archivada");
        }
        if (request.electionType() != null && request.electionType() != election.type) {
            throw new IllegalArgumentException("El tipo del candidato no coincide con la elección seleccionada");
        }
        if (candidates.selectDuplicateNameCount(election.id, name, currentId) > 0) {
            throw new BusinessConflictException("Ya existe un candidato con ese nombre en la elección seleccionada");
        }

        candidate.name = name;
        candidate.party = party;
        candidate.election = election;
        candidate.electionType = election.type;
        candidate.active = request.active() == null || request.active();

        if (election.type == ElectionType.PRESIDENCIA) {
            candidate.vicePresidentName = normalizeRequired(
                    request.vicePresidentName(),
                    "La fórmula vicepresidencial es obligatoria para Presidencia"
            );
            candidate.department = null;
            candidate.municipality = null;
            return;
        }

        candidate.vicePresidentName = null;
        if (election.type == ElectionType.CAMARA) {
            candidate.department = normalizeRequired(
                    request.department(),
                    "El departamento es obligatorio para candidatos a Cámara"
            );
            candidate.municipality = normalizeOptional(request.municipality());
            return;
        }

        candidate.department = null;
        candidate.municipality = null;
    }

    private CandidateResponse toResponse(CandidateRepository.ManagementRow row) {
        long officialResultCount = safe(row.getOfficialResultCount());
        long pollResultCount = safe(row.getPollResultCount());
        return new CandidateResponse(
                row.getId(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getName(),
                row.getVicePresidentName(),
                new CandidateParty(
                        row.getPartyId(),
                        row.getPartyName(),
                        row.getPartyAcronym(),
                        row.getPartyColor(),
                        Boolean.TRUE.equals(row.getPartyActive())
                ),
                new CandidateElection(
                        row.getElectionId(),
                        row.getElectionName(),
                        row.getElectionType(),
                        row.getElectionRound(),
                        row.getElectionDate(),
                        row.getElectionState()
                ),
                row.getElectionType(),
                row.getDepartment(),
                row.getMunicipality(),
                Boolean.TRUE.equals(row.getActive()),
                officialResultCount,
                pollResultCount,
                officialResultCount == 0 && pollResultCount == 0
        );
    }

    private CandidateResponse toResponse(Candidate candidate) {
        long officialResultCount = candidate.id == null ? 0 : candidates.selectOfficialResultCount(candidate.id);
        long pollResultCount = candidate.id == null ? 0 : candidates.selectPollResultCount(candidate.id);
        return new CandidateResponse(
                candidate.id,
                candidate.createdAt,
                candidate.updatedAt,
                candidate.name,
                candidate.vicePresidentName,
                toParty(candidate.party),
                toElection(candidate.election),
                candidate.electionType == null ? null : candidate.electionType.name(),
                candidate.department,
                candidate.municipality,
                candidate.active,
                officialResultCount,
                pollResultCount,
                officialResultCount == 0 && pollResultCount == 0
        );
    }

    private CandidateParty toParty(Party party) {
        return new CandidateParty(party.id, party.name, party.acronym, party.color, party.active);
    }

    private CandidateElection toElection(Election election) {
        return new CandidateElection(
                election.id,
                election.name,
                election.type == null ? null : election.type.name(),
                election.round == null ? null : election.round.name(),
                election.electionDate,
                election.state == null ? null : election.state.name()
        );
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    private long safe(Long value) {
        return value == null ? 0 : value;
    }
}
