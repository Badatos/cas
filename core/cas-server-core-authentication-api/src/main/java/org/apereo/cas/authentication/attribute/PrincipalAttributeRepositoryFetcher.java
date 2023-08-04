package org.apereo.cas.authentication.attribute;

import org.apereo.cas.authentication.principal.Principal;
import org.apereo.cas.authentication.principal.Service;
import org.apereo.services.persondir.IPersonAttributeDao;
import org.apereo.services.persondir.IPersonAttributeDaoFilter;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This is {@link PrincipalAttributeRepositoryFetcher}.
 *
 * @author Misagh Moayyed
 * @since 6.2.0
 */
@SuperBuilder
@Getter
@Slf4j
public class PrincipalAttributeRepositoryFetcher {
    private final IPersonAttributeDao attributeRepository;

    private final String principalId;

    @Builder.Default
    private final Set<String> activeAttributeRepositoryIdentifiers = new HashSet<>();

    @Builder.Default
    private final Map<String, List<Object>> queryAttributes = new HashMap<>();

    private final Principal currentPrincipal;

    private final Service service;

    /**
     * Retrieve person attributes.
     *
     * @return the map
     */
    public Map<String, List<Object>> retrieve() {
        val query = new LinkedHashMap<String, Object>();
        if (currentPrincipal != null) {
            query.put("principal", currentPrincipal.getId());
            query.putAll(currentPrincipal.getAttributes());
        }
        query.putAll(queryAttributes);
        query.put("username", principalId.trim());

        if (service != null) {
            query.put("service", service.getId());
        }

        LOGGER.debug("Fetching person attributes for query [{}]", query);
        val people = attributeRepository.getPeople(query, PrincipalAttributeRepositoryFilter.of(this));
        if (people == null || people.isEmpty()) {
            LOGGER.warn("No person records were fetched from attribute repositories for [{}]", query);
            return new HashMap<>(0);
        }

        if (people.size() > 1) {
            LOGGER.warn("Multiple records were found for [{}] from attribute repositories for query [{}]. The records are [{}], "
                + "and CAS will only pick the first person record from the results.", principalId, query, people);
        }

        val person = people.iterator().next();
        LOGGER.debug("Retrieved person [{}] from attribute repositories for query [{}]", person, query);
        return person.getAttributes();
    }

    @RequiredArgsConstructor(staticName = "of")
    private static class PrincipalAttributeRepositoryFilter implements IPersonAttributeDaoFilter {
        private final PrincipalAttributeRepositoryFetcher fetcher;

        @Override
        public boolean choosePersonAttributeDao(final IPersonAttributeDao repository) {
            val activeAttributeRepositoryIdentifiers = fetcher.getActiveAttributeRepositoryIdentifiers();
            if (activeAttributeRepositoryIdentifiers.isEmpty()) {
                return false;
            }
            if (activeAttributeRepositoryIdentifiers.contains(IPersonAttributeDao.WILDCARD)) {
                return true;
            }

            val repoIdsArray = activeAttributeRepositoryIdentifiers.toArray(ArrayUtils.EMPTY_STRING_ARRAY);
            LOGGER.trace("Active attribute repository identifiers [{}] to compare with [{}}",
                activeAttributeRepositoryIdentifiers, repository.getId());
            val result = Arrays.stream(repository.getId()).anyMatch(daoId -> daoId.equalsIgnoreCase(IPersonAttributeDao.WILDCARD)
                || StringUtils.equalsAnyIgnoreCase(daoId, repoIdsArray)
                || StringUtils.equalsAnyIgnoreCase(IPersonAttributeDao.WILDCARD, repoIdsArray));
            LOGGER.debug("Selecting attribute repository [{}]", ArrayUtils.toString(repository.getId()));
            return result;
        }
    }
}
