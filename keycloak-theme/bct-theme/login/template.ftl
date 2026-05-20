<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false showAnotherWayIfPresent=true>
<!DOCTYPE html>
<html lang="fr" class="${properties.kcHtmlClass!}">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
  <title>${msg("loginTitle",(realm.displayName!''))}</title>
  <link rel="icon" href="${url.resourcesPath}/img/bct-logo.png"/>
  <#if properties.styles?has_content>
    <#list properties.styles?split(' ') as style>
      <link href="${url.resourcesPath}/${style}" rel="stylesheet"/>
    </#list>
  </#if>
  <style>
    body { padding-top: 0 !important; }
  </style>
</head>
<body class="login-pf">

<div class="login-pf-page">
  <div class="container">
    <div id="kc-content">

      <#nested "header">

      <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
        <#-- messages handled inside each template -->
      </#if>

      <#nested "form">

      <#if displayInfo>
        <div id="kc-info">
          <div id="kc-info-wrapper">
            <#nested "info">
          </div>
        </div>
      </#if>

    </div>
  </div>
</div>

</body>
</html>
</#macro>
