package lt.jr.alfresco.scim.api.services;

import lt.jr.alfresco.scim.api.model.GroupInfo;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.cmr.security.PermissionService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service("GroupsService")
public class GroupsServiceImpl implements GroupsService{

    @Autowired
    @Qualifier("AuthorityService")
    private AuthorityService authorityService;

    @Override
    public GroupInfo createGroup(String displayName) {

        String sanitizedName = sanitizeAuthorityShortName(displayName);

        if (StringUtils.isBlank(sanitizedName)) {
            throw new RuntimeException("Invalid group name provided: " + sanitizedName);
        }

        String shortName = StringUtils.substring(sanitizedName, 0, 30);

        if (authorityService.authorityExists(getGroupName(shortName))) {
            int suffix = 0;

            do {
                suffix++;
            } while (authorityService.authorityExists(getGroupName(shortName + "_" + suffix)));

            shortName = shortName + "_" + suffix;
        }

        String fullName = authorityService.createAuthority(AuthorityType.GROUP, shortName, displayName, authorityService.getDefaultZones());

        return new GroupInfo(sanitizedName, displayName, fullName);
    }

    private String sanitizeAuthorityShortName(String name) {
        if (StringUtils.isBlank(name)) {
            return name;
        }
        return name.trim().replaceAll("[^a-zA-Z0-9 \\-_]", "")
                .trim()
                .replaceAll(" ", "_")
                .replaceAll("-", "_");
    }

    private String getGroupName(String shortName) {
        return PermissionService.GROUP_PREFIX + shortName;
    }
}
