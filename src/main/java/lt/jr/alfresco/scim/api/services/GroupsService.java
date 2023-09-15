package lt.jr.alfresco.scim.api.services;

import lt.jr.alfresco.scim.api.model.GroupInfo;

public interface GroupsService {
    GroupInfo createGroup(String displayName);
}
