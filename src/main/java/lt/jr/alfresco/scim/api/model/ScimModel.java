package lt.jr.alfresco.scim.api.model;

import org.alfresco.service.namespace.QName;

public interface ScimModel {
    String URI = "http://jrudinskij.lt/model/scim/1.0";

    QName ASPECT_EXTERNAL_AUTHORITY = QName.createQName(URI, "ExternalAuthority");

    QName PROP_EXTERNAL_ID = QName.createQName(URI, "externalId");

    QName PROP_LAST_SYNC_DATE = QName.createQName(URI, "lastSyncDate");
}
