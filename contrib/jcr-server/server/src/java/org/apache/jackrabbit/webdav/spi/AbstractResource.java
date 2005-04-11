/*
 * Copyright 2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.webdav.spi;

import org.apache.log4j.Logger;
import org.apache.jackrabbit.webdav.*;
import org.apache.jackrabbit.webdav.transaction.TransactionResource;
import org.apache.jackrabbit.webdav.transaction.TransactionInfo;
import org.apache.jackrabbit.webdav.transaction.TransactionConstants;
import org.apache.jackrabbit.webdav.transaction.TxLockManager;
import org.apache.jackrabbit.webdav.spi.transaction.TxLockManagerImpl;
import org.apache.jackrabbit.webdav.observation.*;
import org.apache.jackrabbit.webdav.util.Text;
import org.apache.jackrabbit.webdav.version.*;
import org.apache.jackrabbit.webdav.version.report.Report;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.apache.jackrabbit.webdav.version.report.ReportType;
import org.apache.jackrabbit.webdav.version.report.SupportedReportSetProperty;
import org.apache.jackrabbit.webdav.lock.*;
import org.apache.jackrabbit.webdav.property.*;
import org.apache.jackrabbit.webdav.property.ResourceType;

import javax.jcr.Session;
import java.io.InputStream;
import java.util.*;

/**
 * <code>AbstractResource</code> provides functionality common to all
 * resources.
 */
abstract class AbstractResource implements DavResource, ObservationResource,
        TransactionResource, DeltaVResource {

    private static Logger log = Logger.getLogger(AbstractResource.class);

    private final DavResourceLocator locator;
    private final DavSession session;
    private final DavResourceFactory factory;

    private SubscriptionManager subsMgr;
    private TxLockManagerImpl txMgr;
    private String transactionId;

    private long modificationTime = DavResource.UNDEFINED_MODIFICATIONTIME;

    protected boolean initedProps;
    protected DavPropertySet properties = new DavPropertySet();
    protected SupportedLock supportedLock = new SupportedLock();
    protected SupportedReportSetProperty supportedReports = new SupportedReportSetProperty();

    /**
     * Create a new <code>AbstractResource</code>
     *
     * @param locator
     * @param session
     */
    AbstractResource(DavResourceLocator locator, DavSession session, DavResourceFactory factory) {
        if (session == null) {
            throw new IllegalArgumentException("Creating AbstractItemResource: DavSession must not be null.");
        }

        this.locator = locator;
        this.session = session;
        this.factory = factory;

	// initialize the supported locks and reports
        initLockSupport();
        initSupportedReports();
    }

    /**
     * @see org.apache.jackrabbit.webdav.DavResource#getLocator()
     */
    public DavResourceLocator getLocator() {
        return locator;
    }

    /**
     * Returns the path of the underlaying repository item or the item to
     * be created (PUT/MKCOL). If the resource exists but does not represent
     * a repository item <code>null</code> is returned.
     *
     * @return path of the underlaying repository item.
     * @see DavResource#getResourcePath()
     * @see org.apache.jackrabbit.webdav.DavResourceLocator#getResourcePath()
     */
    public String getResourcePath() {
        return locator.getResourcePath();
    }

    /**
     * @see DavResource#getHref()
     * @see DavResourceLocator#getHref(boolean)
     */
    public String getHref() {
        return locator.getHref(isCollection());
    }

    /**
     * @see org.apache.jackrabbit.webdav.DavResource#getModificationTime()
     */
    public long getModificationTime() {
        return modificationTime;
    }

    /**
     * Set the modificationTime field and adds the {@link DavPropertyName.GETLASTMODIFIED}
     * property to the set of properties.
     * @param modificationTime
     */
    void setModificationTime(long modificationTime) {
        this.modificationTime = modificationTime;
        if (this.modificationTime >= 0) {
            properties.add(new DefaultDavProperty(DavPropertyName.GETLASTMODIFIED,
                    DavConstants.modificationDateFormat.format(new Date(modificationTime))));
        }
    }

    /**
     * Returns <code>null</code>
     *
     * @return Always returns <code>null</code>
     * @see org.apache.jackrabbit.webdav.DavResource#getStream()
     */
    public InputStream getStream() {
        return null;
    }

    /**
     * @see org.apache.jackrabbit.webdav.DavResource#getPropertyNames()
     */
    public DavPropertyName[] getPropertyNames() {
        return getProperties().getPropertyNames();
    }

    /**
     * @see org.apache.jackrabbit.webdav.DavResource#getProperty(org.apache.jackrabbit.webdav.property.DavPropertyName)
     */
    public DavProperty getProperty(DavPropertyName name) {
        return getProperties().get(name);
    }

    /**
     * @see org.apache.jackrabbit.webdav.DavResource#getProperties()
     */
    public DavPropertySet getProperties() {
        if (!initedProps) {
            initProperties();
        }
        return properties;
    }

    /**
     * Throws {@link DavServletResponse#SC_METHOD_NOT_ALLOWED}
     *
     * @param property
     * @throws DavException Always throws {@link DavServletResponse#SC_METHOD_NOT_ALLOWED}
     * @see org.apache.jackrabbit.webdav.DavResource#setProperty(org.apache.jackrabbit.webdav.property.DavProperty)
     */
    public void setProperty(DavProperty property) throws DavException {
        throw new DavException(DavServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    /**
     * Throws {@link DavServletResponse#SC_METHOD_NOT_ALLOWED}
     *
     * @param propertyName
     * @throws DavException Always throws {@link DavServletResponse#SC_METHOD_NOT_ALLOWED}
     * @see org.apache.jackrabbit.webdav.DavResource#removeProperty(org.apache.jackrabbit.webdav.property.DavPropertyName)
     */
    public void removeProperty(DavPropertyName propertyName) throws DavException {
        throw new DavException(DavServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    /**
     * Throws {@link DavServletResponse#SC_METHOD_NOT_ALLOWED}
     *
     * @param destination
     * @throws DavException Always throws {@link DavServletResponse#SC_METHOD_NOT_ALLOWED}
     * @see DavResource#move(org.apache.jackrabbit.webdav.DavResource)
     */
    public void move(DavResource destination) throws DavException {
        throw new DavException(DavServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    /**
     * Throws {@link DavServletResponse#SC_METHOD_NOT_ALLOWED}
     *
     * @param destination
     * @param shallow
     * @throws DavException Always throws {@link DavServletResponse#SC_METHOD_NOT_ALLOWED}
     * @see DavResource#copy(org.apache.jackrabbit.webdav.DavResource, boolean)
     */
    public void copy(DavResource destination, boolean shallow) throws DavException {
        throw new DavException(DavServletResponse.SC_METHOD_NOT_ALLOWED);
    }


    /**
     * Returns true, if the {@link SupportedLock} property contains an entry
     * with the given type and scope. By default resources allow for {@link org.apache.jackrabbit.webdav.transaction.TransactionConstants.XML_TRANSACTION
     * transaction} lock only.
     *
     * @param type
     * @param scope
     * @return true if this resource may be locked by the given type and scope.
     * @see DavResource#isLockable(org.apache.jackrabbit.webdav.lock.Type, org.apache.jackrabbit.webdav.lock.Scope)
     */
    public boolean isLockable(Type type, Scope scope) {
        return supportedLock.isSupportedLock(type, scope);
    }

    /**
     * Returns true if this resource has a lock applied with the given type and scope.
     *
     * @param type
     * @param scope
     * @return true if this resource has a lock applied with the given type and scope.
     * @see DavResource#hasLock(Type, Scope)
     */
    public boolean hasLock(Type type, Scope scope) {
        return getLock(type, scope) != null;
    }

    /**
     * @see DavResource#getLock(Type, Scope)
     */
    public ActiveLock getLock(Type type, Scope scope) {
        ActiveLock lock = null;
        if (TransactionConstants.TRANSACTION.equals(type)) {
            lock = txMgr.getLock(type, scope, this);
        }
        return lock;
    }

    /**
     * @see DavResource#getLocks()
     * todo improve....
     */
    public ActiveLock[] getLocks() {
        List locks = new ArrayList();
        // tx locks
        ActiveLock l = getLock(TransactionConstants.TRANSACTION, TransactionConstants.LOCAL);
        if (l != null) {
            locks.add(l);
        }
        l = getLock(TransactionConstants.TRANSACTION, TransactionConstants.GLOBAL);
        if (l != null) {
            locks.add(l);
        }
        // write lock (either exclusive or session-scoped).
        l = getLock(Type.WRITE, Scope.EXCLUSIVE);
        if (l != null) {
            locks.add(l);
        } else {
            l = getLock(Type.WRITE, ItemResourceConstants.EXCLUSIVE_SESSION);
            if (l != null) {
                locks.add(l);
            }
        }
        return (ActiveLock[]) locks.toArray(new ActiveLock[locks.size()]);
    }

    /**
     * @see DavResource#lock(org.apache.jackrabbit.webdav.lock.LockInfo)
     */
    public ActiveLock lock(LockInfo reqLockInfo) throws DavException {
        if (isLockable(reqLockInfo.getType(), reqLockInfo.getScope())) {
            return txMgr.createLock(reqLockInfo, this);
        } else {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED);
        }
    }

    /**
     * Only transaction lock may be available on this resource.
     *
     * @param info
     * @param lockToken
     * @throws DavException
     * @see DavResource#refreshLock(org.apache.jackrabbit.webdav.lock.LockInfo, String)
     */
    public ActiveLock refreshLock(LockInfo info, String lockToken) throws DavException {
        return txMgr.refreshLock(info, lockToken, this);        
    }

    /**
     * Throws {@link DavServletResponse#SC_METHOD_NOT_ALLOWED} since only transaction
     * locks may be present on this resource, that need to be released by calling
     * {@link TransactionResource#unlock(String, org.apache.jackrabbit.webdav.transaction.TransactionInfo)}.
     *
     * @param lockToken
     * @throws DavException Always throws {@link DavServletResponse#SC_METHOD_NOT_ALLOWED}
     */
    public void unlock(String lockToken) throws DavException {
        throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED);
    }

    /**
     * @see DavResource#addLockManager(org.apache.jackrabbit.webdav.lock.LockManager)
     */
    public void addLockManager(LockManager lockMgr) {
        if (lockMgr instanceof TxLockManagerImpl) {
            txMgr = (TxLockManagerImpl) lockMgr;
        }
    }

    /**
     * @see org.apache.jackrabbit.webdav.DavResource#getFactory()
     */
    public DavResourceFactory getFactory() {
        return factory;
    }

    //--------------------------------------------------------------------------
    /**
     * @see org.apache.jackrabbit.webdav.transaction.TransactionResource#getSession()
     * @see org.apache.jackrabbit.webdav.observation.ObservationResource#getSession()
     */
    public DavSession getSession() {
        return session;
    }

    //--------------------------------------< ObservationResource interface >---
    /**
     * @see ObservationResource#init(SubscriptionManager)
     */
    public void init(SubscriptionManager subsMgr) {
        this.subsMgr = subsMgr;
    }

    /**
     * @see ObservationResource#subscribe(org.apache.jackrabbit.webdav.observation.SubscriptionInfo, String)
     * @see SubscriptionManager#subscribe(org.apache.jackrabbit.webdav.observation.SubscriptionInfo, String, org.apache.jackrabbit.webdav.observation.ObservationResource)
     */
    public Subscription subscribe(SubscriptionInfo info, String subscriptionId)
            throws DavException {
        return subsMgr.subscribe(info, subscriptionId, this);
    }

    /**
     * @see ObservationResource#unsubscribe(String)
     * @see SubscriptionManager#unsubscribe(String, org.apache.jackrabbit.webdav.observation.ObservationResource)
     */
    public void unsubscribe(String subscriptionId) throws DavException {
        subsMgr.unsubscribe(subscriptionId, this);
    }

    /**
     * @see ObservationResource#poll(String)
     * @see SubscriptionManager#poll(String, org.apache.jackrabbit.webdav.observation.ObservationResource)
     */
    public EventDiscovery poll(String subscriptionId) throws DavException {
        return subsMgr.poll(subscriptionId, this);
    }

    //--------------------------------------< TransactionResource interface >---
    /**
     * @see TransactionResource#init(TxLockManager, String)
     */
    public void init(TxLockManager txMgr, String transactionId) {
        this.txMgr = (TxLockManagerImpl) txMgr;
        this.transactionId = transactionId;
    }

    /**
     * @see TransactionResource#unlock(String, org.apache.jackrabbit.webdav.transaction.TransactionInfo)
     */
    public void unlock(String lockToken, TransactionInfo tInfo) throws DavException {
        txMgr.releaseLock(tInfo, lockToken, this);
    }

    /**
     * @see TransactionResource#getTransactionId()
     */
    public String getTransactionId() {
        return transactionId;
    }

    //-------------------------------------------< DeltaVResource interface >---
    /**
     * @param optionsInfo
     * @return object to be used in the OPTIONS response body or <code>null</code>
     * @see DeltaVResource#getOptionResponse(org.apache.jackrabbit.webdav.version.OptionsInfo)
     */
    public OptionsResponse getOptionResponse(OptionsInfo optionsInfo) {
        OptionsResponse oR = null;
        if (optionsInfo != null) {
            oR = new OptionsResponse();
            // currently on DAV:version-history-collection-set and
            // DAV:workspace-collection-set is supported.
            if (optionsInfo.containsElement(DeltaVConstants.XML_VH_COLLECTION_SET, DeltaVConstants.NAMESPACE)) {
                String[] hrefs = new String[] { getLocatorFromResourcePath(ItemResourceConstants.VERSIONSTORAGE_PATH).getHref(true)};
                oR.addEntry(DeltaVConstants.XML_VH_COLLECTION_SET, DeltaVConstants.NAMESPACE, hrefs);
            } else if (optionsInfo.containsElement(DeltaVConstants.XML_WSP_COLLECTION_SET, DeltaVConstants.NAMESPACE)) {
                // workspaces cannot be created anywhere.
                oR.addEntry(DeltaVConstants.XML_WSP_COLLECTION_SET, DeltaVConstants.NAMESPACE, new String[0]);
            }
        }
        return oR;
    }

    /**
     * @param reportInfo
     * @return the requested report
     * @throws DavException
     * @see DeltaVResource#getReport(org.apache.jackrabbit.webdav.version.report.ReportInfo)
     */
    public Report getReport(ReportInfo reportInfo) throws DavException {
        if (reportInfo == null) {
            throw new DavException(DavServletResponse.SC_BAD_REQUEST, "A REPORT request must provide a valid XML request body.");
        }
        if (!exists()) {
            throw new DavException(DavServletResponse.SC_NOT_FOUND);
        }

        if (supportedReports.isSupportedReport(reportInfo)) {
            try {
                Report report = ReportType.getType(reportInfo).createReport();
                report.setInfo(reportInfo);
                report.setResource(this);
                return report;
            } catch (IllegalArgumentException e) {
                // should never occur.
                throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            }
        } else {
            throw new DavException(DavServletResponse.SC_UNPROCESSABLE_ENTITY, "Unkown report "+ reportInfo.getReportElement().getNamespacePrefix() + reportInfo.getReportElement().getName() +"requested.");
        }
    }

    /**
     * The JCR api does not provide methods to create new workspaces. Calling
     * <code>addWorkspace</code> on this resource will always fail.
     *
     * @param workspace
     * @throws DavException Always throws.
     * @see DeltaVResource#addWorkspace(org.apache.jackrabbit.webdav.DavResource)
     */
    public void addWorkspace(DavResource workspace) throws DavException {
        throw new DavException(DavServletResponse.SC_FORBIDDEN);
    }

    /**
     * Return an array of <code>DavResource</code> objects that are referenced
     * by the property with the specified name.
     *
     * @param hrefPropertyName
     * @return array of <code>DavResource</code>s
     * @throws DavException
     * @see DeltaVResource#getReferenceResources(org.apache.jackrabbit.webdav.property.DavPropertyName)
     */
    public DavResource[] getReferenceResources(DavPropertyName hrefPropertyName) throws DavException {
        DavProperty prop = getProperty(hrefPropertyName);
        if (prop == null || !(prop instanceof HrefProperty)) {
            throw new DavException(DavServletResponse.SC_CONFLICT, "Unknown Href-Property '"+hrefPropertyName+"' on resource "+getResourcePath());
        }

        List hrefs = ((HrefProperty)prop).getHrefs();
        DavResource[] refResources = new DavResource[hrefs.size()];
        Iterator hrefIter = hrefs.iterator();
        int i = 0;
        while (hrefIter.hasNext()) {
            refResources[i] = getResourceFromHref((String)hrefIter.next());
            i++;
        }
        return refResources;
    }

    /**
     * Retrieve the <code>DavResource</code> object that is represented by
     * the given href String.
     *
     * @param href
     * @return <code>DavResource</code> object
     */
    private DavResource getResourceFromHref(String href) throws DavException {
        // build a new locator: remove trailing prefix
        DavResourceLocator locator = getLocator();
        String prefix = locator.getPrefix();
        if (href.startsWith(prefix)) {
            href = href.substring(prefix.length());
        }
        DavResourceLocator loc = locator.getFactory().createResourceLocator(prefix, href);

        // create a new resource object
        DavResource res;
        if (getRepositorySession().itemExists(loc.getResourcePath())) {
            res = createResourceFromLocator(loc);
        } else {
            throw new DavException(DavServletResponse.SC_NOT_FOUND);
        }
        return res;
    }

    //--------------------------------------------------------------------------
    /**
     * Fill the set of default properties
     */
    protected void initProperties() {
        if (getDisplayName() != null) {
            properties.add(new DefaultDavProperty(DavPropertyName.DISPLAYNAME, getDisplayName()));
        }
        if (isCollection()) {
            properties.add(new ResourceType(ResourceType.COLLECTION));
            // Windows XP support
            properties.add(new DefaultDavProperty(DavPropertyName.ISCOLLECTION, "1"));
        } else {
            properties.add(new ResourceType(ResourceType.DEFAULT_RESOURCE));
            // Windows XP support
            properties.add(new DefaultDavProperty(DavPropertyName.ISCOLLECTION, "0"));
        }
        // todo: add etag

        // default last modified
        setModificationTime(new Date().getTime());
        // default creation time
        properties.add(new DefaultDavProperty(DavPropertyName.CREATIONDATE, DavConstants.creationDateFormat.format(new Date(0))));

        // supported lock property
        properties.add(supportedLock);

        // set current lock information. If no lock is applied to this resource,
        // an empty lockdiscovery will be returned in the response.
        properties.add(new LockDiscovery(getLocks()));

        // observation resource
        SubscriptionDiscovery subsDiscovery = subsMgr.getSubscriptionDiscovery(this);
        properties.add(subsDiscovery);

        properties.add(new SupportedMethodSetProperty(getSupportedMethods().split(",\\s")));

	// DeltaV properties
	properties.add(supportedReports);
	// creator-displayname, comment: not value available from jcr
	properties.add(new DefaultDavProperty(DeltaVConstants.CREATOR_DISPLAYNAME, null, true));
	properties.add(new DefaultDavProperty(DeltaVConstants.COMMENT, null, true));

	// 'workspace' property as defined by RFC 3253
	String workspaceHref = getWorkspaceHref();
	if (workspaceHref != null) {
	    properties.add(new HrefProperty(DeltaVConstants.WORKSPACE, workspaceHref, true));
	}
	// TODO: required supported-live-property-set
    }

    /**
     * Create a new <code>DavResource</code> from the given locator.
     * @param loc
     * @return new <code>DavResource</code>
     */
    protected DavResource createResourceFromLocator(DavResourceLocator loc)
            throws DavException {
        DavResource res = factory.createResource(loc, session);
        if (res instanceof AbstractResource) {
            ((AbstractResource)res).transactionId = this.transactionId;
        }
        return res;
    }

    /**
     * Build a <code>DavResourceLocator</code> from the given resource path.
     *
     * @param resourcePath
     * @return a new <code>DavResourceLocator</code>
     * @see DavLocatorFactory#createResourceLocator(String, String, String)
     */
    protected DavResourceLocator getLocatorFromResourcePath(String resourcePath) {
        DavResourceLocator loc = locator.getFactory().createResourceLocator(locator.getPrefix(), locator.getWorkspacePath(), resourcePath);
        return loc;
    }

    /**
     * Retrieve the name/label of a repository item from the given href by
     * splitting of the part after the last slash. If the removeIndex
     * flag is set to true, any trailing index (e.g. '[1]') will be removed.
     *
     * @param resourceHref
     * @param removeIndex
     * @return the name of the item
     */
    protected static String getResourceName(String resourceHref, boolean removeIndex) {
        if (resourceHref == null) {
            return resourceHref;
        }

        // cut the extension
        int pos = resourceHref.lastIndexOf('.');
        if (pos > 0) {
            resourceHref = resourceHref.substring(pos+1);
        } else if (resourceHref.endsWith("/")) {
            resourceHref = resourceHref.substring(0, resourceHref.length()-1);
        }

        // retrieve the last part of the path
        String name = Text.getLabel(resourceHref);
        // remove index
        if (removeIndex) {
            if (name.endsWith("]")) {
                name = name.substring(0, name.lastIndexOf('['));
            }
        }
        return name;
    }

    /**
     * Shortcut for <code>getSession().getRepositorySession()</code>
     *
     * @return repository session present in the {@link #session}.
     */
    protected Session getRepositorySession() {
        return getSession().getRepositorySession();
    }

    /**
     * Define the set of locks supported by this resource.
     *
     * @see org.apache.jackrabbit.webdav.lock.SupportedLock
     */
    abstract protected void initLockSupport();

    /**
     * Define the set of reports supported by this resource.
     *
     * @see org.apache.jackrabbit.webdav.version.report.SupportedReportSetProperty
     */
    abstract protected void initSupportedReports();

    /**
     * Retrieve the href of the workspace the current session belongs to.
     *
     * @return href of the workspace
     */
    abstract protected String getWorkspaceHref();
}