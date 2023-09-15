# SCIM API Endpoint
This addon implements SCIM API endpoint to provision users and groups from external identity providers (e.g. Azure AD Provisioning Service)
to Alfresco.
It allows to sync users and groups using SCIM (System for Cross-domain Identity Management) protocol.
## Compatibility
The addon is built to be compatible with Alfresco 5.2 and above.
## Features
- The addon allows you automatically create and update users and groups during sync process with external identity provider.
- The sync process is initiated and controlled by external system (e.g. Azure AD Provisioning Service)
- Alfresco is not required to know anything about external identity provider.
### What happens if I delete provisioned user in Alfresco. Issues with Azure AD provisioning.
It is strongly recommended not to delete any user in Alfresco since it may cause integrity issues for either Alfresco data as well as for Azure AD provisioning.  
If you delete a user in Alfresco the Azure AD will not be able to find the user, and report an error.  
The Azure AD Provisioning service will not be able to recreate the user due to Azure AD SCIM client implementation subtleties.  
The only way to fix the error is to delete the enterprise application you configured for provisioning and create a new one using the steps provided above.  
To delete an enterprise application use the Properties → Delete menu on the application page.