package co.edu.elecciones.config;

import co.edu.elecciones.domain.*;
import co.edu.elecciones.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Configuration
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer {

    @Value("${app.seed.admin-name}")
    private String adminName;
    @Value("${app.seed.admin-email}")
    private String adminEmail;
    @Value("${app.seed.admin-password}")
    private String adminPassword;
    @Value("${app.seed.analyst-name}")
    private String analystName;
    @Value("${app.seed.analyst-email}")
    private String analystEmail;
    @Value("${app.seed.analyst-password}")
    private String analystPassword;

    @Bean
    CommandLineRunner seed(
            UserRepository users,
            PartyRepository parties,
            CandidateRepository candidates,
            ElectionRepository elections,
            PollRepository polls,
            PollResultRepository pollResults,
            OfficialResultRepository results,
            PasswordEncoder encoder
    ) {
        return args -> initialize(users, parties, candidates, elections, polls, pollResults, results, encoder);
    }

    @Transactional
    void initialize(
            UserRepository users,
            PartyRepository parties,
            CandidateRepository candidates,
            ElectionRepository elections,
            PollRepository polls,
            PollResultRepository pollResults,
            OfficialResultRepository results,
            PasswordEncoder encoder
    ) {
        createUserIfMissing(users, encoder, adminName, adminEmail, adminPassword, Role.ADMINISTRADOR);
        createUserIfMissing(users, encoder, analystName, analystEmail, analystPassword, Role.ANALISTA);

        if (parties.selectCount() != 0) {
            return;
        }

        Party liberal = new Party();
        liberal.name = "Partido Liberal Colombiano";
        liberal.acronym = "PL";
        liberal.color = "#DC143C";
        liberal.foundationYear = 1848;
        liberal = parties.save(liberal);

        Party centroDemocratico = new Party();
        centroDemocratico.name = "Coalición Centro Democrático";
        centroDemocratico.acronym = "CD";
        centroDemocratico.color = "#2563EB";
        centroDemocratico.foundationYear = 2013;
        centroDemocratico = parties.save(centroDemocratico);

        Candidate maria = new Candidate();
        maria.name = "María Fernández";
        maria.vicePresidentName = "Carlos Rojas";
        maria.party = liberal;
        maria.electionType = ElectionType.PRESIDENCIA;
        maria = candidates.save(maria);

        Candidate juan = new Candidate();
        juan.name = "Juan Rodríguez";
        juan.vicePresidentName = "Ana Torres";
        juan.party = centroDemocratico;
        juan.electionType = ElectionType.PRESIDENCIA;
        juan = candidates.save(juan);

        Election election = new Election();
        election.name = "Presidencia Colombia 2026 - Primera vuelta";
        election.type = ElectionType.PRESIDENCIA;
        election.round = ElectionRound.PRIMERA;
        election.electionDate = LocalDate.of(2026, 5, 31);
        election.state = ElectionState.EN_CONTEO;
        election = elections.save(election);

        OfficialResult firstResult = new OfficialResult();
        firstResult.election = election;
        firstResult.candidate = maria;
        firstResult.department = "Bogotá D.C.";
        firstResult.municipality = "Bogotá";
        firstResult.votes = 4_250_000L;
        firstResult.percentage = 38.5;
        firstResult.reportedTables = 88_245;
        firstResult.totalTables = 98_500;
        firstResult.participation = 68.2;
        results.save(firstResult);

        OfficialResult secondResult = new OfficialResult();
        secondResult.election = election;
        secondResult.candidate = juan;
        secondResult.department = "Bogotá D.C.";
        secondResult.municipality = "Bogotá";
        secondResult.votes = 3_850_000L;
        secondResult.percentage = 34.8;
        secondResult.reportedTables = 88_245;
        secondResult.totalTables = 98_500;
        secondResult.participation = 68.2;
        results.save(secondResult);

        Poll poll = new Poll();
        poll.source = "Centro Nacional de Consultoría";
        poll.date = LocalDate.of(2026, 3, 15);
        poll.sampleSize = 2_500;
        poll.marginError = 2.0;
        poll.methodology = "Telefónica";
        poll = polls.save(poll);

        PollResult mariaPoll = new PollResult();
        mariaPoll.poll = poll;
        mariaPoll.candidate = maria;
        mariaPoll.percentage = 36.5;
        pollResults.save(mariaPoll);

        PollResult juanPoll = new PollResult();
        juanPoll.poll = poll;
        juanPoll.candidate = juan;
        juanPoll.percentage = 34.0;
        pollResults.save(juanPoll);
    }

    private void createUserIfMissing(
            UserRepository users,
            PasswordEncoder encoder,
            String name,
            String email,
            String rawPassword,
            Role role
    ) {
        if (users.selectEmailCount(email) != 0) {
            return;
        }

        AppUser user = new AppUser();
        user.name = name;
        user.email = email;
        user.password = encoder.encode(rawPassword);
        user.role = role;
        users.save(user);
    }
}
