package lt.jr.alfresco.scim.api.hadlers;

import de.captaingoldfish.scim.sdk.common.resources.ResourceNode;
import de.captaingoldfish.scim.sdk.server.endpoints.ResourceHandler;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchParameters;
import org.alfresco.service.cmr.search.SearchService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.lowerCase;

public abstract class ExternalAuthorityResourceHandler<T extends ResourceNode> extends ResourceHandler<T>{

    private static final String AUTHORITY_BY_EXTERNAL_ID_CMIS = "SELECT cmis:objectId FROM scim:ExternalAuthority WHERE LOWER(scim:externalId)='%s'";
    @Autowired
    @Qualifier("SearchService")
    private SearchService searchService;
    
    protected Optional<NodeRef> getNodeRefByExternalId(String externalId) {
        String query = String.format(AUTHORITY_BY_EXTERNAL_ID_CMIS, lowerCase(externalId));
        SearchParameters sp = new SearchParameters();
        sp.setQuery(query);
        sp.setLanguage(SearchService.LANGUAGE_CMIS_ALFRESCO);
        sp.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
        return executeQuery(sp)
                .getNodeRefs()
                .stream()
                .findAny();
    }

    private ResultSet executeQuery(SearchParameters sp) {
        ResultSet results = null;
        try {
            results = searchService.query(sp);
            return results;
        } finally {
            if (results != null) {
                results.close();
            }
        }
    }
}
