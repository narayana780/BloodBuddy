/**
 * Blood Buddy — Frontend Interactions & Health Verification
 * Stage 10 Final Polish: Seamless modal navigation, health checking, and accessibility.
 */
document.addEventListener('DOMContentLoaded', () => {
    // 1. Backend Connectivity & Health Check
    const backendStatusEl = document.getElementById('backendStatus');
    const liveNetworkPill = document.getElementById('liveNetworkPill');

    async function checkBackendHealth() {
        try {
            const resp = await fetch('/api/status', { cache: 'no-store' });
            if (resp.ok) {
                const data = await resp.json();
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
        if (backendStatusEl) {
            backendStatusEl.innerHTML = '<span class="status-dot resting"></span> Backend Disconnected';
            backendStatusEl.style.color = 'var(--neon-coral)';
        }
    }

    checkBackendHealth();

    // 2. Journey Modal Handling
    const journeyModal = document.getElementById('journeyModal');
    const modalCloseBtn = document.getElementById('modalCloseBtn');
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

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && journeyModal && journeyModal.classList.contains('active')) {
            closeModal();
        }
    });

    // 3. Journey Card Navigation
    const journeyCards = document.querySelectorAll('.journey-card');
    journeyCards.forEach(card => {
        card.addEventListener('click', () => {
            const role = card.getAttribute('data-role');
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

    // 4. Smooth Scrolling for Anchor Links
    howItWorksBtns.forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.preventDefault();
            closeModal();
            const target = document.getElementById('how-it-works');
            if (target) {
                target.scrollIntoView({ behavior: 'smooth' });
            } else {
                window.location.href = '/#how-it-works';
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
