package lt.jr.alfresco.scim.api.hadlers;

import com.google.common.collect.ImmutableList;
import de.captaingoldfish.scim.sdk.common.constants.enums.SortOrder;
import de.captaingoldfish.scim.sdk.common.resources.User;
import de.captaingoldfish.scim.sdk.common.resources.complex.Name;
import de.captaingoldfish.scim.sdk.common.resources.multicomplex.Email;
import de.captaingoldfish.scim.sdk.common.resources.multicomplex.MultiComplexNode;
import de.captaingoldfish.scim.sdk.common.resources.multicomplex.PhoneNumber;
import de.captaingoldfish.scim.sdk.common.schemas.SchemaAttribute;
import de.captaingoldfish.scim.sdk.server.endpoints.authorize.Authorization;
import de.captaingoldfish.scim.sdk.server.filter.FilterNode;
import de.captaingoldfish.scim.sdk.server.filter.resources.FilterResourceResolver;
import de.captaingoldfish.scim.sdk.server.response.PartialListResponse;
import lt.jr.alfresco.scim.api.model.ScimModel;
import org.alfresco.model.ContentModel;
import org.alfresco.query.PagingRequest;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.security.PersonService.PersonInfo;
import org.alfresco.service.namespace.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Component("UserHandler")
public class UserHandler extends ExternalAuthorityResourceHandler<User>{
    
    private final Logger logger = LoggerFactory.getLogger(UserHandler.class);
    
    @Autowired
    @Qualifier("PersonService")
    private PersonService personService;
    @Autowired
    @Qualifier("NodeService")
    private NodeService nodeService;

    @Override
    public User createResource(User user, Authorization authorization) {
        Optional<NodeRef> userRef = user.getExternalId()
            .flatMap(this::getNodeRefByExternalId);        
        if(userRef.isPresent()) {
            user.setId(userRef.get().getId());
            updateResource(user, authorization);
            return user;
        }
        logger.info("Creating user {}", user.getUserName().get());
        NodeRef newUserRef = personService.createPerson(mapUserProperties(user));
        logger.info("User {} id: {}", user.getUserName().get(), newUserRef.getId());
        user.setId(newUserRef.getId());
        updateUserAspects(newUserRef, user);
        return user;
    }

    @Override
    public User getResource(String id, Authorization authorization, List<SchemaAttribute> attributes, List<SchemaAttribute> excludedAttributes) {
        return getUser(id);
    }

    @Override
    public PartialListResponse<User> listResources(long startIndex, int count, FilterNode filter, SchemaAttribute sortBy, SortOrder sortOrder,
            List<SchemaAttribute> attributes, List<SchemaAttribute> excludedAttributes, Authorization authorization) {
       
        PagingRequest paging = new PagingRequest((int) startIndex - 1, count);
        List<User> users = personService.getPeople(null, null, null, paging)
                .getPage()
                .stream()
                .map(this::getUser)
                .collect(Collectors.toList());
        return PartialListResponse.<User>builder()
                .resources(FilterResourceResolver.filterResources(users, filter))
                .build();
    }

    @Override
    public User updateResource(User user, Authorization authorization) {
        NodeRef userRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, user.getId().get());
        logger.info("Updating user {}", userRef.getId());
        Map<QName, Serializable> props = mapUserProperties(user);
        props.remove(ContentModel.PROP_USERNAME);
        nodeService.addProperties(userRef, props);
        updateUserAspects(userRef, user);
        return user;
    }

    @Override
    public void deleteResource(String id, Authorization authorization) {
        NodeRef personRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, id);
        logger.info("Removing user {}", id);
        nodeService.addAspect(personRef, ContentModel.ASPECT_PERSON_DISABLED, null);
        Optional.ofNullable((String) nodeService.getProperty(personRef, ContentModel.PROP_EMAIL))
            .ifPresent(email -> {
                nodeService.setProperty(personRef, ContentModel.PROP_EMAIL, new Date().getTime() + "_" + email);
            });
    }
    
    private void updateUserAspects(NodeRef personRef, User user) {
        user.isActive().ifPresent(active -> {
            if(active) {
                nodeService.removeAspect(personRef, ContentModel.ASPECT_PERSON_DISABLED);
            } else {
                nodeService.addAspect(personRef, ContentModel.ASPECT_PERSON_DISABLED, null);
            }
        });
    }
    
    private Map<QName, Serializable> mapUserProperties(User user) {
        Map<QName, Serializable> props = new HashMap<>();
        user.getUserName().ifPresent(userName -> props.put(ContentModel.PROP_USERNAME, userName));
        user.getName().ifPresent(name -> {
            name.getFamilyName().ifPresent(lastName -> props.put(ContentModel.PROP_LASTNAME, lastName));
            name.getGivenName().ifPresent(firstName -> props.put(ContentModel.PROP_FIRSTNAME, firstName));
        });
        
        user.getExternalId().ifPresent(externalId -> props.put(ScimModel.PROP_EXTERNAL_ID, externalId));

        user.getTitle().ifPresent(title -> props.put(ContentModel.PROP_JOBTITLE, title));
        
        user.getPhoneNumbers()
            .stream()
            .filter(phone -> phone.getType().filter("mobile"::equals).isPresent())
            .findAny()
            .flatMap(MultiComplexNode::getValue)
            .ifPresent(phone -> props.put(ContentModel.PROP_MOBILE, phone));
        
        user.getPhoneNumbers()
            .stream()
            .filter(phone -> phone.getType().filter("work"::equals).isPresent())
            .findAny()
            .flatMap(MultiComplexNode::getValue)
            .ifPresent(phone -> props.put(ContentModel.PROP_TELEPHONE, phone));
        
        user.getEmails().stream()
            .map(Email::getValue)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst()
            .ifPresent(email -> props.put(ContentModel.PROP_EMAIL, email));
        
        props.put(ScimModel.PROP_LAST_SYNC_DATE, new Date());
        return props;
    }
    
    private User getUser(String id) {
        NodeRef personRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, id);
        if(!nodeService.exists(personRef)) {
            return null;
        }
        PersonInfo person = personService.getPerson(personRef);
        return getUser(person);
    }
    
    private User getUser(PersonInfo person) {
        NodeRef personRef = person.getNodeRef();
        Map<QName, Serializable> props = nodeService.getProperties(personRef);
        String email = (String) props.get(ContentModel.PROP_EMAIL);
        String mobile = (String) props.get(ContentModel.PROP_MOBILE);
        String phone = (String) props.get(ContentModel.PROP_TELEPHONE);
        return User.builder()
                .id(personRef.getId())
                .name(Name.builder()
                        .givenName(person.getFirstName())
                        .familyName(person.getLastName())
                        .build())
                .userName(person.getUserName())
                .emails(ImmutableList.of(
                            Email.builder()
                            .type("work")
                            .value(email)
                            .build()))
                .phoneNumbers(ImmutableList.of(
                        PhoneNumber.builder()
                            .type("mobile")
                            .value(mobile)
                            .build(),
                        PhoneNumber.builder()
                            .type("work")
                            .value(phone)
                            .build()
                        ))
                .title((String) props.get(ContentModel.PROP_JOBTITLE))
                .active(!nodeService.hasAspect(personRef, ContentModel.ASPECT_PERSON_DISABLED))
                .build();
    }

}
