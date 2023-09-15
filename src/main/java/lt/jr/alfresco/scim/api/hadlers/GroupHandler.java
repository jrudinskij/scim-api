package lt.jr.alfresco.scim.api.hadlers;

import de.captaingoldfish.scim.sdk.common.constants.enums.SortOrder;
import de.captaingoldfish.scim.sdk.common.resources.Group;
import de.captaingoldfish.scim.sdk.common.resources.multicomplex.Member;
import de.captaingoldfish.scim.sdk.common.schemas.SchemaAttribute;
import de.captaingoldfish.scim.sdk.server.endpoints.authorize.Authorization;
import de.captaingoldfish.scim.sdk.server.filter.FilterNode;
import de.captaingoldfish.scim.sdk.server.filter.resources.FilterResourceResolver;
import de.captaingoldfish.scim.sdk.server.response.PartialListResponse;
import lt.jr.alfresco.scim.api.model.GroupInfo;
import lt.jr.alfresco.scim.api.model.ScimModel;
import lt.jr.alfresco.scim.api.services.GroupsService;
import org.alfresco.model.ContentModel;
import org.alfresco.query.PagingRequest;
import org.alfresco.repo.security.authority.AuthorityInfo;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.namespace.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component("GroupHandler")
public class GroupHandler extends ExternalAuthorityResourceHandler<Group> {
    
    private final Logger logger = LoggerFactory.getLogger(GroupHandler.class);
    
    @Autowired
    @Qualifier("AuthorityService")
    private AuthorityService authorityService;
    @Autowired
    @Qualifier("GroupsService")
    private GroupsService groupsService;
    @Autowired
    @Qualifier("NodeService")
    private NodeService nodeService;

    @Override
    public Group createResource(Group group, Authorization authorization) {
        Optional<NodeRef> groupRef = group.getExternalId()
                .flatMap(this::getNodeRefByExternalId);
        if(groupRef.isPresent()) {
            group.setId(groupRef.get().getId());
            updateResource(group, authorization);
            return group;
        }
        String groupDisplayName = group.getDisplayName().get();
        logger.info("Creating group {}", groupDisplayName);
        GroupInfo groupInfo = groupsService.createGroup(groupDisplayName);
        NodeRef newGroupRef = authorityService.getAuthorityNodeRef(groupInfo.getFullName());
        group.setId(newGroupRef.getId());
        logger.info("Group {} id: {}", groupDisplayName, newGroupRef.getId());
        updateResource(group, authorization);
        return group;
    }

    @Override
    public Group getResource(String id, Authorization authorization, List<SchemaAttribute> attributes, List<SchemaAttribute> excludedAttributes) {
        NodeRef groupRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, id);
        if(!nodeService.exists(groupRef)) {
            return null;
        }
        String displayName = (String) nodeService.getProperty(groupRef, ContentModel.PROP_AUTHORITY_DISPLAY_NAME);
        Group group = new Group();
        group.setId(id);
        group.setDisplayName(displayName);
        
        String groupName = getAuthorityName(group.getId().get());
        List<Member> members = authorityService.getContainedAuthorities(null, groupName, true)
            .stream()
            .map(this::getMember)
            .collect(Collectors.toList());
        group.setMembers(members);
        
        return group;
    }

    @Override
    public PartialListResponse<Group> listResources(long startIndex, int count, FilterNode filter, SchemaAttribute sortBy, SortOrder sortOrder,
            List<SchemaAttribute> attributes, List<SchemaAttribute> excludedAttributes, Authorization authorization) {
        PagingRequest paging = new PagingRequest((int) startIndex - 1, count);
        List<Group> groups = authorityService.getAuthoritiesInfo(AuthorityType.GROUP, AuthorityService.ZONE_APP_DEFAULT, null, null, true, paging)
                .getPage()
                .stream()
                .map(this::getGroup)
                .collect(Collectors.toList());
        return PartialListResponse.<Group>builder()
                .resources(FilterResourceResolver.filterResources(groups, filter))
                .build();
    }

    @Override
    public Group updateResource(Group group, Authorization authorization) {
        NodeRef groupRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, group.getId().get()); 
        logger.info("Updating group {}", groupRef.getId());
        group.getDisplayName().ifPresent(displayName -> {
            nodeService.setProperty(groupRef, ContentModel.PROP_AUTHORITY_DISPLAY_NAME, displayName);    
        });
        group.getExternalId().ifPresent(externalId -> nodeService.setProperty(groupRef, ScimModel.PROP_EXTERNAL_ID, externalId));
        updateMembers(group);
        return group;
    }

    @Override
    public void deleteResource(String id, Authorization authorization) {
        NodeRef groupRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, id);
        String groupName = (String) nodeService.getProperty(groupRef, ContentModel.PROP_AUTHORITY_NAME);
        logger.info("Removing group {} {}", groupName, id);
        authorityService.deleteAuthority(groupName);
    }
    
    private void updateMembers(Group group) {
        String groupName = getAuthorityName(group.getId().get());
        logger.info("Updating group {} members: {}", group.getId().get(), group.getMembers().stream().map(Object::toString).collect(Collectors.joining()));
        Set<String> currentMembers = group.getMembers().stream()
            .map(member -> member.getValue())
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(this::getAuthorityName)
            .collect(Collectors.toSet());
        
        Set<String> oldMembers = authorityService.getContainedAuthorities(null, groupName, true);
        oldMembers.stream()
            .filter(member -> !currentMembers.contains(member))
            .forEach(member -> this.removeMember(groupName, member));
        
        currentMembers.stream()
            .filter(member -> !oldMembers.contains(member))
            .forEach(memberName ->
                addMember(groupName, memberName)
            );
    }
    
    private void removeMember(String groupName, String member) {
        logger.info("Removing member \"{}\" from group {}", member, groupName);
        authorityService.removeAuthority(groupName, member);
    }
    
    private void addMember(String groupName, String member) {
        logger.info("Adding member \"{}\" from group {}", member, groupName);
        authorityService.addAuthority(groupName, member);
    }
    
    private String getAuthorityName(String id) {
        NodeRef authRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, id);
        QName authType = nodeService.getType(authRef);
        if(ContentModel.TYPE_AUTHORITY_CONTAINER.equals(authType)) {
            return (String) nodeService.getProperty(authRef, ContentModel.PROP_AUTHORITY_NAME);
        }
        return (String) nodeService.getProperty(authRef, ContentModel.PROP_USERNAME);
    }
    
    private Group getGroup(AuthorityInfo group) {
        return Group.builder()
                .id(authorityService.getAuthorityNodeRef(group.getAuthorityName()).getId())
                .displayName(group.getAuthorityDisplayName())
                .build();
    }
    
    private Member getMember(String authorityName) {
        return Member.builder()
            .value(authorityService.getAuthorityNodeRef(authorityName).getId())
            .build();
    }
}
