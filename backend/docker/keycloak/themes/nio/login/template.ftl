<#import "footer.ftl" as loginFooter>
<#import "theme-resources.ftl" as themeResourceTags>
<#-- Based on Keycloak 26.8.1 base/login/template.ftl: same macro contract and scripts, salon layout. -->
<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false>
<!DOCTYPE html>
<html class="${properties.kcHtmlClass!}" lang="${lang}"<#if realm.internationalizationEnabled> dir="${(locale.rtl)?then('rtl','ltr')}"</#if>>

<head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />

    <#if properties.meta?has_content>
        <#list properties.meta?split(' ') as meta>
            <meta name="${meta?split('==')[0]}" content="${meta?split('==')[1]}"/>
        </#list>
    </#if>
    <title>${msg("nioPageTitle")}</title>
    <script>
        // Before first paint: the site passes its theme choice as ?nio_theme= on the auth URL (frontend/src/auth/keycloak.ts).
        // Later pages in the flow (form posts, links) drop the query, so the choice is kept in this origin's storage.
        // No explicit choice means no data-theme, and nio.css follows the OS setting.
        (function () {
            var theme = new URLSearchParams(location.search).get('nio_theme')
            try {
                if (theme === 'light' || theme === 'dark') localStorage.setItem('nio.theme', theme)
                else if (theme === 'system') localStorage.removeItem('nio.theme')
                else theme = localStorage.getItem('nio.theme')
            } catch (e) {}
            if (theme === 'light' || theme === 'dark') document.documentElement.dataset.theme = theme
        })()
    </script>
    <link rel="icon" href="${url.resourcesPath}/img/favicon.ico" />
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <#if properties.scripts?has_content>
        <#list properties.scripts?split(' ') as script>
            <script src="${url.resourcesPath}/${script}" type="text/javascript"></script>
        </#list>
    </#if>
    <script type="importmap">
        {
            "imports": {
                "rfc4648": "${url.resourcesCommonPath}/vendor/rfc4648/rfc4648.js"
            }
        }
    </script>
    <#if scripts??>
        <#list scripts as script>
            <script src="${script}" type="text/javascript"></script>
        </#list>
    </#if>
    <script type="module">
        <#outputformat "JavaScript">
        import { startSessionPolling } from ${(url.resourcesPath + "/js/authChecker.js")?c};

        startSessionPolling(
            ${url.ssoLoginInOtherTabsUrl?c}
        );
        </#outputformat>
    </script>
    <script type="module">
        document.addEventListener("click", (event) => {
            const link = event.target.closest("a[data-once-link]");

            if (!link) {
                return;
            }

            if (link.getAttribute("aria-disabled") === "true") {
                event.preventDefault();
                return;
            }

            const { disabledClass } = link.dataset;

            if (disabledClass) {
                link.classList.add(...disabledClass.trim().split(/\s+/));
            }

            link.setAttribute("role", "link");
            link.setAttribute("aria-disabled", "true");
        });
    </script>
    <#if authenticationSession??>
        <script type="module">
            <#outputformat "JavaScript">
            import { checkAuthSession } from ${(url.resourcesPath + "/js/authChecker.js")?c};

            checkAuthSession(
                ${authenticationSession.authSessionIdHash?c}
            );
            </#outputformat>
        </script>
    </#if>
</head>

<body class="${properties.kcBodyClass!} ${bodyClass}" data-page-id="login-${pageId}">
<div class="nio-page">
    <nav class="nio-topbar">
        <#if client?? && client.baseUrl?has_content>
            <a class="nio-back label-caps" href="${client.baseUrl}">&larr; ${msg("nioBackToSite")}</a>
        <#else>
            <span></span>
        </#if>
        <#if realm.internationalizationEnabled && locale.supported?size gt 1>
            <ul class="nio-locales" aria-label="${msg("languages")}">
                <#list locale.supported as l>
                    <li>
                        <a class="label-caps<#if l.languageTag == locale.currentLanguageTag> is-current</#if>"
                           href="${l.url}" hreflang="${l.languageTag}" lang="${l.languageTag}"
                           <#if l.languageTag == locale.currentLanguageTag>aria-current="true"</#if>>${l.languageTag?upper_case}</a>
                    </li>
                </#list>
            </ul>
        </#if>
    </nav>

    <main class="nio-card">
        <header class="nio-card-header">
            <svg class="nio-badge" viewBox="0 0 200 300" role="img" aria-label="Salon Nio Esthétique">
                <path d="M14 292 V100 A86 86 0 0 1 186 100 V292 Z" class="nio-badge-fill" />
                <path d="M22 284 V100 A78 78 0 0 1 178 100 V284 Z" fill="none" stroke="currentColor" stroke-width="1" />
                <path d="M88 92 C112 112, 92 150, 112 176 C126 196, 104 222, 120 248 C104 228, 88 200, 98 172 C84 146, 104 118, 88 92 Z" class="nio-badge-blush" opacity="0.85" />
                <path d="M96 104 C116 130, 98 160, 118 196" fill="none" class="nio-badge-stroke" stroke-width="6" stroke-linecap="round" opacity="0.6" />
                <path id="nio-badge-arc" d="M32 152 A68 68 0 0 1 168 152" fill="none" />
                <text class="nio-font-display" font-size="14" letter-spacing="1.1" fill="currentColor">
                    <textPath href="#nio-badge-arc" startOffset="50%" text-anchor="middle">SALON NIO ESTHÉTIQUE</textPath>
                </text>
                <line x1="52" y1="250" x2="148" y2="250" stroke="currentColor" stroke-width="0.75" />
                <text x="100" y="264" text-anchor="middle" class="nio-font-sans" font-size="8.5" letter-spacing="1.8" fill="currentColor">AESTHETICIAN SERVICES</text>
                <line x1="52" y1="272" x2="148" y2="272" stroke="currentColor" stroke-width="0.75" />
            </svg>

            <#if !(auth?has_content && auth.showUsername() && !auth.showResetCredentials())>
                <h1 id="kc-page-title" class="nio-title"><#nested "header"></h1>
            <#else>
                <#nested "show-username">
                <div id="kc-username" class="nio-attempted">
                    <span id="kc-attempted-username">${auth.attemptedUsername}</span>
                    <a id="reset-login" class="link" href="${url.loginRestartFlowUrl}" aria-label="${msg("restartLoginTooltip")}">${msg("nioNotYou")}</a>
                </div>
            </#if>
            <#if displayRequiredFields>
                <p class="nio-required-note"><span class="required">*</span> ${msg("requiredFields")}</p>
            </#if>
        </header>

        <div id="kc-content">
            <#-- App-initiated actions should not see warning messages about the need to complete the action during login. -->
            <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
                <div class="${properties.kcAlertClass!} nio-alert-${message.type}" role="<#if message.type = 'error'>alert<#else>status</#if>">
                    <span class="${properties.kcAlertTitleClass!}">${kcSanitize(message.summary)?no_esc}</span>
                </div>
            </#if>

            <#nested "form">

            <#if auth?has_content && auth.showTryAnotherWayLink()>
                <form id="kc-select-try-another-way-form" action="${url.loginAction}" method="post">
                    <div class="${properties.kcFormGroupClass!}">
                        <input type="hidden" name="tryAnotherWay" value="on"/>
                        <a href="#" id="try-another-way" class="link"
                           onclick="document.forms['kc-select-try-another-way-form'].requestSubmit();return false;">${msg("doTryAnotherWay")}</a>
                    </div>
                </form>
            </#if>

            <#if switchOrganizationEnabled?? && switchOrganizationEnabled>
                <form id="kc-switch-organization-form" action="${url.loginAction}" method="post">
                    <div class="${properties.kcFormGroupClass!}">
                        <input type="hidden" name="switchOrganization" value="true"/>
                        <a href="#" id="switch-organization" class="link"
                           onclick="document.forms['kc-switch-organization-form'].requestSubmit();return false;">${msg("doSwitchOrganization")}</a>
                    </div>
                </form>
            </#if>

            <#nested "socialProviders">

            <#if displayInfo>
                <div id="kc-info" class="nio-info">
                    <#nested "info">
                </div>
            </#if>
        </div>

        <@loginFooter.content/>
    </main>

    <p class="nio-signature"><span class="nio-font-script">Esthétique</span></p>
</div>
</body>
</html>
</#macro>
