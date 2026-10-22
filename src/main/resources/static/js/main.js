/**
 * Blood Buddy — Frontend Interactions & Health Verification
 * GitHub Pages static preview support with dynamic backend requirement modals.
 */
document.addEventListener('DOMContentLoaded', () => {
    // 1. Backend Connectivity & Health Check
    const backendStatusEl = document.getElementById('backendStatus');
    const liveNetworkPill = document.getElementById('liveNetworkPill');
    let isBackendActive = false;

    async function checkBackendHealth() {
        try {
            const p = window.location.pathname;
            const apiPath = p.indexOf('/BloodBuddy') === 0 ? '/BloodBuddy/api/status' : '/api/status';
            const resp = await fetch(apiPath, { cache: 'no-store' });
            if (resp.ok) {
                const data = await resp.json();
                isBackendActive = true;
                if (backendStatusEl) {
                    backendStatusEl.innerHTML = '<span class="status-dot"></span> Backend Online (' + (data.app || 'Blood Buddy') + ' v' + (data.version || '1.0') + ')';
                    backendStatusEl.style.color = 'var(--mint)';
                }
                if (liveNetworkPill) {
                    liveNetworkPill.title = 'Tomcat Server Active • Port ' + (data.port || 8080);
                }
            } else {
                markBackendOffline();
            }
        } catch (e) {
            markBackendOffline();
        }
    }

    function markBackendOffline() {
        isBackendActive = false;
        if (backendStatusEl) {
            backendStatusEl.innerHTML = '<span class="status-dot resting"></span> Static Preview Mode (Backend Offline)';
            backendStatusEl.style.color = 'var(--neon-coral)';
        }
    }

    checkBackendHealth();

    // 2. Journey Modal Handling
    const journeyModal = document.getElementById('journeyModal');
    const modalCloseBtn = journeyModal ? journeyModal.querySelector('[data-action="close-modal"]') : null;
    const getStartedBtns = document.querySelectorAll('[data-action="get-started"]');
    const howItWorksBtns = document.querySelectorAll('[data-action="how-it-works"]');

    function openModal() {
        if (journeyModal) {
            journeyModal.classList.add('active');
            journeyModal.setAttribute('aria-hidden', 'false');
            document.body.style.overflow = 'hidden';
            if (modalCloseBtn) modalCloseBtn.focus();
        }
    }

    function closeModal() {
        if (journeyModal) {
            journeyModal.classList.remove('active');
            journeyModal.setAttribute('aria-hidden', 'true');
            document.body.style.overflow = '';
        }
    }

    getStartedBtns.forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.preventDefault();
            openModal();
        });
    });

    if (modalCloseBtn) {
        modalCloseBtn.addEventListener('click', closeModal);
    }

    if (journeyModal) {
        journeyModal.addEventListener('click', (e) => {
            if (e.target === journeyModal) {
                closeModal();
            }
        });
    }

    // 3. Backend Requirement Notice Modal (for GitHub Pages static preview)
    const backendNoticeModal = document.getElementById('backendNoticeModal');
    const backendNoticeCloseBtn = document.getElementById('backendNoticeCloseBtn');
    const backendNoticeDismissBtn = document.getElementById('backendNoticeDismissBtn');
    const backendNoticeDesc = document.getElementById('backendNoticeDesc');

    function openBackendNotice(featureName) {
        closeModal();
        if (backendNoticeModal) {
            if (backendNoticeDesc && featureName) {
                backendNoticeDesc.textContent = "The '" + featureName + "' feature requires the active Blood Buddy Java/Tomcat backend with persistent JSON storage.";
            }
            backendNoticeModal.classList.add('active');
            backendNoticeModal.setAttribute('aria-hidden', 'false');
            document.body.style.overflow = 'hidden';
            if (backendNoticeCloseBtn) backendNoticeCloseBtn.focus();
        }
    }

    function closeBackendNotice() {
        if (backendNoticeModal) {
            backendNoticeModal.classList.remove('active');
            backendNoticeModal.setAttribute('aria-hidden', 'true');
            document.body.style.overflow = '';
        }
    }

    if (backendNoticeCloseBtn) backendNoticeCloseBtn.addEventListener('click', closeBackendNotice);
    if (backendNoticeDismissBtn) backendNoticeDismissBtn.addEventListener('click', closeBackendNotice);
    if (backendNoticeModal) {
        backendNoticeModal.addEventListener('click', (e) => {
            if (e.target === backendNoticeModal) {
                closeBackendNotice();
            }
        });
    }

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            if (journeyModal && journeyModal.classList.contains('active')) closeModal();
            if (backendNoticeModal && backendNoticeModal.classList.contains('active')) closeBackendNotice();
        }
    });

    // 4. Intercept Server Routes when running on GitHub Pages (static mode)
    const isGitHubPages = window.location.hostname.endsWith('github.io') || window.location.protocol === 'file:';

    document.querySelectorAll('a[href]').forEach(link => {
        const href = link.getAttribute('href');
        if (!href) return;

        // Skip internal anchors and external links
        if (href.startsWith('#') || href.startsWith('tel:') || href.startsWith('mailto:') || href.startsWith('http://') || href.startsWith('https://')) {
            return;
        }

        // Server-side dynamic routes
        const serverRoutes = [
            '/find-donors', '/become-donor', '/request-blood', '/view-requests',
            '/camps', '/notifications', '/login', '/register', '/admin',
            '/dashboard', '/compatibility', '/about', '/help', '/chat', '/messages'
        ];

        const matchesServerRoute = serverRoutes.some(route => href === route || href.startsWith(route + '?') || href.startsWith(route + '/'));

        if (matchesServerRoute) {
            link.addEventListener('click', (e) => {
                if (!isBackendActive || isGitHubPages) {
                    e.preventDefault();
                    const featureTitle = link.textContent.trim().replace(/[→↓🔔🚨]/g, '').trim() || href;
                    openBackendNotice(featureTitle);
                }
            });
        }
    });

    // 5. Journey Card Navigation
    const journeyCards = document.querySelectorAll('.journey-card');
    journeyCards.forEach(card => {
        card.addEventListener('click', () => {
            const role = card.getAttribute('data-role');
            if (!isBackendActive || isGitHubPages) {
                openBackendNotice(role === 'Donor' ? 'Voluntary Donor Registration' : 'Patient Blood Requisition');
                return;
            }
            if (role === 'Donor') {
                window.location.href = '/become-donor';
            } else if (role === 'Recipient') {
                window.location.href = '/request-blood';
            } else {
                window.location.href = '/dashboard';
            }
        });
        // Keyboard access for cards
        card.setAttribute('tabindex', '0');
        card.setAttribute('role', 'button');
        card.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                card.click();
            }
        });
    });

    // 6. Smooth Scrolling for Anchor Links
    howItWorksBtns.forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.preventDefault();
            closeModal();
            const target = document.getElementById('how-it-works');
            if (target) {
                target.scrollIntoView({ behavior: 'smooth' });
            } else {
                window.location.href = '#how-it-works';
            }
        });
    });

    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function(e) {
            const href = this.getAttribute('href');
            if (href !== '#' && href.length > 1) {
                const target = document.querySelector(href);
                if (target) {
                    e.preventDefault();
                    target.scrollIntoView({ behavior: 'smooth' });
                }
            }
        });
    });
});
