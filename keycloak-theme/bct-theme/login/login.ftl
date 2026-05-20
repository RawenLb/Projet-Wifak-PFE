<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
    <#if section = "header">
        &nbsp;

    <#elseif section = "form">

        <div id="wf-top-bar">
            <div class="wf-brand">
                <div class="wf-logo-box">
                    <img src="${url.resourcesPath}/img/bct-logo.png" alt="WIFAK BANK" />
                </div>
                <div class="wf-brand-text">
                    <strong>WIFAK BANK</strong>
                    <span>Portail d'Administration BCT</span>
                </div>
            </div>
        </div>

        <div id="wf-body">

            <#if message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
                <div class="wf-alert wf-alert-${message.type}">
                    <span class="wf-alert-bar"></span>
                    <span>${kcSanitize(message.summary)?no_esc}</span>
                </div>
            </#if>

            <h2 class="wf-title">Connexion à la <em>Plateforme</em></h2>
            <p class="wf-desc">Accédez à votre espace de pilotage des déclarations réglementaires exigées par la BCT.</p>

            <form id="kc-form-login" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post" autocomplete="off">

                <div class="wf-field">
                    <label for="username">
                        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                        <#if !realm.loginWithEmailAllowed>Identifiant<#elseif !realm.registrationEmailAsUsername>Identifiant ou e-mail<#else>Adresse e-mail</#if>
                    </label>
                    <#if usernameHidden??>
                        <input type="hidden" id="username" name="username" value="${(login.username!'')}" disabled/>
                    <#else>
                        <input tabindex="1" id="username" name="username" type="text"
                               value="${(login.username!'')}"
                               autofocus autocomplete="off"
                               placeholder="votre@email.com"
                               class="wf-input<#if messagesPerField.existsError('username','password')> wf-err-input</#if>"
                        />
                        <#if messagesPerField.existsError('username','password')>
                            <span class="wf-field-err">${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}</span>
                        </#if>
                    </#if>
                </div>

                <div class="wf-field">
                    <label for="password">
                        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                        Mot de passe
                    </label>
                    <div class="wf-pwd-wrap">
                        <input tabindex="2" id="password" name="password" type="password"
                               autocomplete="off"
                               placeholder="••••••••"
                               class="wf-input wf-input-pwd<#if usernameHidden?? && messagesPerField.existsError('username','password')> wf-err-input</#if>"
                        />
                        <button type="button" class="wf-eye" onclick="wfEye()" aria-label="Voir le mot de passe">
                            <svg id="wf-eye-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                        </button>
                    </div>
                    <#if usernameHidden?? && messagesPerField.existsError('username','password')>
                        <span class="wf-field-err">${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}</span>
                    </#if>
                </div>

                <div class="wf-row">
                    <#if realm.rememberMe && !usernameHidden??>
                        <label class="wf-check">
                            <input tabindex="3" type="checkbox" name="rememberMe" <#if login.rememberMe??>checked</#if>/>
                            <span class="wf-checkmark"></span>
                            <span>Se souvenir de moi</span>
                        </label>
                    </#if>
                    <#if realm.resetPasswordAllowed>
                        <a tabindex="5" href="${url.loginResetCredentialsUrl}" class="wf-forgot">Mot de passe oublié ?</a>
                    </#if>
                </div>

                <input type="hidden" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>

                <button tabindex="4" id="kc-login" type="submit" name="login">
                    <span>Se connecter</span>
                    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" class="wf-arrow"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
                </button>

            </form>
        </div>

        <#if realm.password && social.providers??>
            <div style="padding: 0 36px 24px;">
                <div class="wf-sep"><span>ou continuer avec</span></div>
                <ul style="list-style:none;">
                    <#list social.providers as p>
                        <li><a href="${p.loginUrl}" class="wf-social"><#if p.iconClasses?has_content><i class="${p.iconClasses!}"></i></#if>${p.displayName!}</a></li>
                    </#list>
                </ul>
            </div>
        </#if>

        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <div class="wf-register">
                <span>Pas encore de compte ?</span>
                <a tabindex="6" href="${url.registrationUrl}">S'inscrire</a>
            </div>
        </#if>

        <script>
            function wfEye() {
                var p = document.getElementById('password');
                var i = document.getElementById('wf-eye-icon');
                if (p.type === 'password') {
                    p.type = 'text';
                    i.innerHTML = '<path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19M1 1l22 22" stroke="currentColor" stroke-width="2" fill="none"/>';
                } else {
                    p.type = 'password';
                    i.innerHTML = '<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>';
                }
            }
        </script>
    </#if>
</@layout.registrationLayout>
