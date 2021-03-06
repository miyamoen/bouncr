<#import "../../layout/defaultLayout.ftl" as layout>
<@layout.layout "List of applications">
  <ol class="breadcrumb">
    <li class="breadcrumb-item"><a href="${urlFor('net.unit8.bouncr.web.controller.admin.IndexController', 'home')}">Administration</a></li>
    <li class="breadcrumb-item active">Applications</li>
  </ol>
  <h1>List of applications</h1>

  <#list applications>
  <table class="table">
    <thead>
      <tr>
        <th>${t('field.name')}</th>
        <th>Virtual path</th>
        <th>Pass to</th>
        <th></th>
      </tr>
    </thead>
    <tbody>
      <#items as application>
        <tr>
          <td>
          <#if application.writeProtected>
            ${application.name}
          <#else>
            <a href="${urlFor('edit?id=' + application.id)}">${application.name}</a>
          </#if>
          </td>
          <td>${application.virtualPath}</td>
          <td>${application.passTo}</td>
          <td>
            <a href="${urlFor('net.unit8.bouncr.web.controller.admin.RealmController', 'listByApplicationId?applicationId=' + application.id)}">Realms</a>
          </td>
        </tr>
      </#items>
    </tbody>
  </table>
  <#else>
  <div class="alert alert-info" role="alert">
     <p>No applications</p>
  </div>
  </#list>

  <a href="${urlFor('newForm')}">New register</a>
</@layout.layout>
