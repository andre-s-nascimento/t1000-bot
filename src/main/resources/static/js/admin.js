/**
 * T-Mill Admin — JavaScript principal
 * Extraído do <script> inline do admin.html (Fase 4a)
 *
 * Depende de: bootstrap.bundle.min.js (carregado antes, no HTML)
 */
(function () {
    "use strict";

    // ================================================================
    // TEMA (claro/escuro)
    // ================================================================
    window.toggleTheme = function () {
        var current = document.documentElement.getAttribute('data-bs-theme') || 'light';
        var next = current === 'light' ? 'dark' : 'light';
        document.documentElement.setAttribute('data-bs-theme', next);
        localStorage.setItem('tmill-theme', next);
        updateThemeUI();
    };

    function updateThemeUI() {
        var current = document.documentElement.getAttribute('data-bs-theme') || 'light';
        var icon = document.getElementById('themeIcon');
        var label = document.getElementById('themeLabel');
        if (icon && label) {
            if (current === 'dark') {
                icon.className = 'bi bi-sun';
                label.textContent = 'Claro';
            } else {
                icon.className = 'bi bi-moon-stars';
                label.textContent = 'Escuro';
            }
        }
    }

    // ================================================================
    // CSRF — helpers tolerantes a null
    // ================================================================
    function csrfMeta(name, fallback) {
        var el = document.querySelector("meta[name='" + name + "']");
        return el ? el.getAttribute('content') : fallback;
    }

    function csrfHeaders() {
        var headerName = csrfMeta('_csrf_header', null);
        var token = csrfMeta('_csrf', null);
        if (!headerName || !token) return {};
        var h = {};
        h[headerName] = token;
        return h;
    }

    function csrfHiddenInputs() {
        var param = csrfMeta('_csrf_parameter', '_csrf');
        var token = csrfMeta('_csrf', '');
        return '<input type="hidden" name="' + param + '" value="' + token + '">';
    }

    // ================================================================
    // FETCH SEGURO — sempre lê como texto e tenta parsear JSON
    // ================================================================
    async function fetchJson(url, options) {
        var resp = await fetch(url, options);
        var text = await resp.text();

        var data;
        try {
            data = text ? JSON.parse(text) : {};
        } catch (e) {
            var preview = text.substring(0, 200).replace(/\s+/g, ' ');
            var msg =
                'Servidor retornou HTTP ' + resp.status +
                ' sem JSON. Provavelmente sessão expirada ou CSRF. ' +
                'Faça login novamente. Trecho: ' + preview;
            throw new Error(msg);
        }

        if (!resp.ok) {
            throw new Error(data.erro || data.message || ('HTTP ' + resp.status));
        }
        return data;
    }

    // ================================================================
    // FLASH MESSAGES
    // ================================================================
    document.addEventListener('DOMContentLoaded', function () {
        setTimeout(function () {
            document.querySelectorAll('.flash-message').forEach(function (el) {
                var alert = bootstrap.Alert.getInstance(el);
                if (alert) alert.close();
            });
        }, 5000);
    });

    // ================================================================
    // CONFIRM DATA-ATTR (substitui onsubmit="return confirm(...)")
    // ================================================================
    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('form[data-confirm]').forEach(function (form) {
            form.addEventListener('submit', function (e) {
                if (!confirm(form.dataset.confirm)) {
                    e.preventDefault();
                }
            });
        });
        document.querySelectorAll('button[data-confirm]').forEach(function (btn) {
            var form = btn.closest('form');
            if (!form) return;
            form.addEventListener('submit', function (e) {
                if (!confirm(btn.dataset.confirm)) {
                    e.preventDefault();
                }
            });
        });
    });

    // ================================================================
    // AJAX GENÉRICO (fala-t1000, tts, etc.)
    // ================================================================
    window.submitAction = async function (event, url, form) {
        event.preventDefault();
        var formData = new FormData(form);
        var params = new URLSearchParams(formData);
        var resultDiv = form.parentElement.querySelector('output[id$="Result"]');
        var spinner = form.querySelector('.spinner');

        if (spinner) {
            spinner.style.display = 'inline-block';
            form.setAttribute('aria-busy', 'true');
        }
        if (resultDiv) {
            resultDiv.innerHTML = '<span class="spinner-border text-primary" role="status"></span>';
        }

        try {
            var response = await fetch(url + '?' + params.toString(), {
                method: 'POST',
                headers: csrfHeaders()
            });
            var text = await response.text();
            if (resultDiv) {
                resultDiv.innerHTML = '<div class="alert alert-info mb-0">' + text + '</div>';
            }
        } catch (error) {
            if (resultDiv) {
                resultDiv.innerHTML = '<div class="alert alert-danger mb-0">Erro: ' + error.message + '</div>';
            }
        } finally {
            if (spinner) {
                spinner.style.display = 'none';
                form.removeAttribute('aria-busy');
            }
        }
    };

    // ================================================================
    // DEBUG AUTO-RESPONSE
    // ================================================================
    window.debugAutoResponse = async function (event, form) {
        event.preventDefault();
        var params = new URLSearchParams({
            userId: document.getElementById('debugUserId').value,
            message: document.getElementById('debugMessage').value,
            time: document.getElementById('debugTime').value
        });
        var resultDiv = document.getElementById('debugResult');
        resultDiv.innerHTML = '<span class="spinner-border text-primary" role="status"></span>';

        try {
            var data = await fetchJson('/admin-web/debug-auto-response?' + params.toString());
            resultDiv.innerHTML = '<pre>' + JSON.stringify(data, null, 2) + '</pre>';
        } catch (error) {
            resultDiv.innerHTML = '<div class="alert alert-danger mb-0">Erro: ' + error.message + '</div>';
        }
    };

    // ================================================================
    // ANIVERSÁRIOS
    // ================================================================
    window.loadBirthdays = async function () {
        var container = document.getElementById('birthdaysList');
        if (!container) return;
        try {
            var data = await fetchJson('/admin-web/birthdays-json');

            if (!data.birthdays || data.birthdays.length === 0) {
                container.innerHTML = '<p class="text-muted mb-0">Nenhum aniversário cadastrado.</p>';
                return;
            }

            var csrfFields = csrfHiddenInputs();

            var rows = data.birthdays.map(function (b) {
                var dia = String(b.day).padStart(2, '0');
                var mes = String(b.month).padStart(2, '0');
                return '<tr>' +
                    '<td>' + b.userName + '</td>' +
                    '<td>' + dia + '/' + mes + '</td>' +
                    '<td>' + (b.lastSentYear || '-') + '</td>' +
                    '<td>' +
                    '<form action="/admin-web/delete-birthday" method="post" style="display:inline" ' +
                    'data-confirm="Remover aniversário?">' +
                    csrfFields +
                    '<input type="hidden" name="userId" value="' + b.userId + '">' +
                    '<button type="submit" class="btn btn-sm btn-outline-danger" ' +
                    'aria-label="Remover aniversário de ' + escapeHtml(b.userName) + '">' +
                    '<span class="bi bi-trash3" aria-hidden="true"></span>' +
                    '</button></form></td></tr>';
            }).join('');

            container.innerHTML =
                '<table class="table table-sm mb-0">' +
                '<thead><tr><th scope="col">Nome</th><th scope="col">Data</th>' +
                '<th scope="col">Último envio</th><th scope="col"><span class="visually-hidden">Ações</span></th>' +
                '</tr></thead>' +
                '<tbody>' + rows + '</tbody></table>' +
                '<p class="text-muted small mt-2 mb-0">Total: ' + data.total + '</p>';

            container.querySelectorAll('form[data-confirm]').forEach(function (form) {
                form.addEventListener('submit', function (e) {
                    if (!confirm(form.dataset.confirm)) e.preventDefault();
                });
            });
        } catch (e) {
            container.innerHTML = '<div class="alert alert-danger mb-0">Erro: ' + e.message + '</div>';
        } finally {
            container.removeAttribute('aria-busy');
        }
    };

    // ================================================================
    // FEATURE FLAGS
    // ================================================================
    window.loadFeatureFlags = async function () {
        var container = document.getElementById('featureFlagsContainer');
        if (!container) return;
        container.setAttribute('aria-busy', 'true');
        container.innerHTML = '<div class="text-center p-3">' +
            '<span class="spinner-border text-primary" role="status"></span></div>';

        try {
            var data = await fetchJson('/admin-web/features');
            renderFeatureFlags(container, data);
        } catch (e) {
            container.innerHTML = '<div class="alert alert-warning m-3 mb-0">' +
                '<span class="bi bi-info-circle" aria-hidden="true"></span> ' +
                'Erro ao carregar feature flags: ' + e.message +
                '</div>';
        } finally {
            container.removeAttribute('aria-busy');
        }
    };

    function renderFeatureFlags(container, flags) {
        if (!flags || Object.keys(flags).length === 0) {
            container.innerHTML = '<p class="text-muted p-3 mb-0">Nenhuma feature flag configurada.</p>';
            return;
        }
        var html = '';
        Object.keys(flags).sort().forEach(function (key) {
            var f = flags[key];
            var checked = f.enabled ? 'checked' : '';
            var disabled = f.readOnly ? 'disabled aria-disabled="true"' : '';
            var ariaChecked = f.enabled ? 'true' : 'false';
            var roBadge = f.readOnly
                ? '<span class="badge text-bg-warning ms-2">read-only</span>'
                : '';
            html += '<div class="feature-flag-row">' +
                '<div class="flex-grow-1 me-3">' +
                '<div class="flag-name">' + escapeHtml(key) + roBadge + '</div>' +
                '<div class="flag-desc">' + escapeHtml(f.description || '') + '</div>' +
                '</div>' +
                '<div class="form-check form-switch">' +
                '<input class="form-check-input" type="checkbox" role="switch" ' +
                'id="flag-' + escapeHtml(key) + '" ' + checked + ' ' + disabled + ' ' +
                'aria-checked="' + ariaChecked + '" ' +
                'aria-label="Ativar/desativar flag ' + escapeHtml(key) + '" ' +
                'onchange="toggleFeatureFlag(\'' + key + '\', this.checked, this)">' +
                '</div></div>';
        });
        container.innerHTML = html;
    }

    window.toggleFeatureFlag = async function (key, enabled, checkbox) {
        checkbox.disabled = true;
        try {
            var data = await fetchJson('/admin/features/' + encodeURIComponent(key), {
                method: 'POST',
                headers: Object.assign(
                    { 'Content-Type': 'application/x-www-form-urlencoded' },
                    csrfHeaders()
                ),
                body: 'enabled=' + enabled
            });
            checkbox.setAttribute('aria-checked', data.enabled ? 'true' : 'false');
            showToast(
                'Flag "' + key + '" ' + (data.enabled ? 'ativada' : 'desativada'),
                'success'
            );
            // Fase 3: reaplica em todos os data-feature
            if (typeof applyFeatureFlags === 'function') {
                applyFeatureFlags();
            }
        } catch (e) {
            showToast('Erro ao alterar flag: ' + e.message, 'danger');
            checkbox.checked = !enabled;
            checkbox.setAttribute('aria-checked', (!enabled) ? 'true' : 'false');
        } finally {
            checkbox.disabled = false;
        }
    };

    // ================================================================
    // UPLOAD JSON — confirmação antes de enviar
    // ================================================================
    window.confirmUploadJson = function (form) {
        var target = form.uploadTarget.value;
        var file = form.uploadFile.files[0];
        if (!target || !file) {
            showToast('Selecione o alvo e o arquivo antes de enviar.', 'warning');
            return false;
        }
        if (file.size > 2 * 1024 * 1024) {
            showToast('Arquivo maior que 2 MB. Envie um arquivo menor.', 'danger');
            return false;
        }
        return confirm(
            'Substituir "' + target + '" pelo arquivo "' + file.name + '"?\n\n' +
            'O serviço será recarregado imediatamente após o upload.'
        );
    };

    // ================================================================
    // MIGRAÇÃO
    // ================================================================
    window.previewMigration = async function () {
        var div = document.getElementById('migrationPreview');
        div.style.display = 'block';
        div.innerHTML = '<span class="spinner-border text-info" role="status"></span>';
        try {
            var data = await fetchJson('/admin-web/migrate-sqlite/preview');
            div.innerHTML = '<pre>' + JSON.stringify(data, null, 2) + '</pre>';
        } catch (e) {
            div.innerHTML = '<div class="alert alert-danger mb-0">Erro: ' + e.message + '</div>';
        }
    };

    // ================================================================
    // DEBUG CACHE
    // ================================================================
    window.debugCache = async function (event, form) {
        event.preventDefault();
        var fileId = form.fileId.value;
        var div = document.getElementById('cacheDebugResult');
        div.style.display = 'block';
        div.innerHTML = '<span class="spinner-border text-info" role="status"></span>';
        try {
            var data = await fetchJson('/admin-web/debug/cache/' + encodeURIComponent(fileId));
            div.innerHTML = '<pre>' + JSON.stringify(data, null, 2) + '</pre>';
        } catch (e) {
            div.innerHTML = '<div class="alert alert-danger mb-0">Erro: ' + e.message + '</div>';
        }
    };

    // ================================================================
    // TOAST
    // ================================================================
    window.showToast = function (message, variant) {
        var toast = document.createElement('div');
        toast.className = 'alert alert-' + variant + ' flash-message';
        toast.setAttribute('role', 'alert');
        toast.textContent = message;
        document.body.appendChild(toast);
        setTimeout(function () {
            toast.remove();
        }, 5000);
    };

    // ================================================================
    // ESCAPE HTML (helper global)
    // ================================================================
    if (typeof window.escapeHtml !== 'function') {
        window.escapeHtml = function (s) {
            return String(s == null ? '' : s)
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#39;');
        };
    }

    // ================================================================
    // ANÚNCIOS (REMINDERS) — CRUD + agendamento dinâmico
    // ================================================================
    (function () {
        'use strict';

        var API = '/admin/reminders';

        var modalEl = null;
        var modal = null;
        var editingId = null;
        var availableChatIds = [];

        function initRemindersModule() {
            modalEl = document.getElementById('reminderModal');
            if (modalEl) {
                modal = new bootstrap.Modal(modalEl);
            }
            availableChatIds = collectAvailableChatIds();
            populateChatsSelect();
        }

        function collectAvailableChatIds() {
            var ids = new Set();
            document.querySelectorAll('select[name="chatId"] option').forEach(function (opt) {
                var v = (opt.value || '').trim();
                if (v && !isNaN(Number(v))) {
                    ids.add(v);
                }
            });
            return Array.from(ids);
        }

        function populateChatsSelect() {
            var sel = document.getElementById('reminderChats');
            if (!sel) return;
            var current = new Set(Array.from(sel.selectedOptions).map(function (o) { return o.value; }));
            sel.innerHTML = '';
            availableChatIds.forEach(function (id) {
                var opt = document.createElement('option');
                opt.value = id;
                opt.textContent = id;
                if (current.has(id)) opt.selected = true;
                sel.appendChild(opt);
            });
        }

        window.loadReminders = async function () {
            var container = document.getElementById('remindersContainer');
            var status = document.getElementById('remindersStatus');
            if (!container) return;

            container.setAttribute('aria-busy', 'true');
            container.innerHTML = '<div class="text-center p-4">' +
                '<span class="spinner-border text-primary" role="status">' +
                '</span></div>';

            try {
                var data = await fetchJson(API);
                renderReminders(data);
                if (status) status.textContent = data.length + ' anúncio(s)';
            } catch (e) {
                container.innerHTML = '<div class="alert alert-danger mb-0">' +
                    '<span class="bi bi-exclamation-triangle" aria-hidden="true"></span> ' +
                    'Erro ao carregar anúncios: ' + escapeHtml(e.message) + '</div>';
                if (status) status.textContent = '';
            } finally {
                container.removeAttribute('aria-busy');
            }
        };

        function renderReminders(reminders) {
            var container = document.getElementById('remindersContainer');
            if (!reminders || reminders.length === 0) {
                container.innerHTML = '<div class="alert alert-light border mb-0 text-center">' +
                    '<span class="bi bi-inbox" aria-hidden="true"></span> ' +
                    'Nenhum anúncio cadastrado. Clique em <b>Novo Anúncio</b> para começar.</div>';
                return;
            }

            var rows = reminders.map(function (r) {
                var enabledBadge = r.enabled
                    ? '<span class="badge text-bg-success">habilitado</span>'
                    : '<span class="badge text-bg-secondary">desabilitado</span>';
                var mediaBadge = r.mediaUrl
                    ? '<span class="badge text-bg-info" title="Envia mídia + legenda">mídia</span>'
                    : '';
                var chatsCount = (r.targetChatIds || []).length;
                var safeId = escapeHtml(r.id);
                var safeName = escapeHtml(r.name || r.id);

                return '<tr>' +
                    '<td><code>' + safeId + '</code></td>' +
                    '<td>' + safeName + '</td>' +
                    '<td><code class="small">' + escapeHtml(r.cron) + '</code></td>' +
                    '<td>' + enabledBadge + ' ' + mediaBadge + '</td>' +
                    '<td>' + chatsCount + ' chat(s)</td>' +
                    '<td class="text-end">' +
                    '<button type="button" class="btn btn-sm btn-outline-secondary me-1" ' +
                    'onclick="toggleReminder(\'' + safeId + '\', ' + (!r.enabled) + ')" ' +
                    'aria-label="' + (r.enabled ? 'Desabilitar' : 'Habilitar') + ' anúncio ' + safeId + '">' +
                    '<span class="bi bi-' + (r.enabled ? 'pause' : 'play') + '" aria-hidden="true"></span>' +
                    '</button>' +
                    '<button type="button" class="btn btn-sm btn-outline-success me-1" ' +
                    'onclick="testReminder(\'' + safeId + '\')" ' +
                    'aria-label="Testar anúncio ' + safeId + '">' +
                    '<i class="bi bi-send" aria-hidden="true"></i>' +
                    '</button>' +
                    '<button type="button" class="btn btn-sm btn-outline-primary me-1" ' +
                    'onclick="openReminderModal(\'' + safeId + '\')" ' +
                    'aria-label="Editar anúncio ' + safeId + '">' +
                    '<span class="bi bi-pencil" aria-hidden="true"></span>' +
                    '</button>' +
                    '<button type="button" class="btn btn-sm btn-outline-danger" ' +
                    'onclick="deleteReminder(\'' + safeId + '\')" ' +
                    'aria-label="Excluir anúncio ' + safeId + '">' +
                    '<span class="bi bi-trash3" aria-hidden="true"></span>' +
                    '</button>' +
                    '</td></tr>';
            }).join('');

            container.innerHTML =
                '<div class="table-responsive">' +
                '<table class="table table-sm table-hover align-middle mb-0">' +
                '<thead><tr>' +
                '<th scope="col">ID</th>' +
                '<th scope="col">Nome</th>' +
                '<th scope="col">Cron</th>' +
                '<th scope="col">Status</th>' +
                '<th scope="col">Chats</th>' +
                '<th scope="col" class="text-end">Ações</th>' +
                '</tr></thead>' +
                '<tbody>' + rows + '</tbody>' +
                '</table></div>';
        }

        window.openReminderModal = async function (id) {
            if (!modal) {
                showToast('Modal não disponível. Recarregue a página.', 'danger');
                return;
            }

            resetReminderForm();
            editingId = id || null;

            if (editingId) {
                document.getElementById('reminderModalTitle').textContent = 'Editar Anúncio';
                try {
                    var r = await fetchJson(API + '/' + encodeURIComponent(editingId));
                    fillReminderForm(r);
                } catch (e) {
                    showToast('Erro ao carregar anúncio: ' + e.message, 'danger');
                    return;
                }
            } else {
                document.getElementById('reminderModalTitle').textContent = 'Novo Anúncio';
            }

            modal.show();
        };

        function resetReminderForm() {
            var form = document.getElementById('reminderForm');
            if (form) form.reset();

            document.getElementById('reminderId').disabled = false;
            document.getElementById('reminderEnabled').checked = true;
            document.getElementById('reminderEnabled').setAttribute('aria-checked', 'true');
            document.getElementById('reminderTimezone').value = 'America/Sao_Paulo';
            document.getElementById('reminderId').value = '';
            document.getElementById('reminderName').value = '';
            document.getElementById('reminderCron').value = '';
            document.getElementById('reminderMessage').value = '';
            document.getElementById('reminderMediaUrl').value = '';
            document.getElementById('reminderTags').value = '';

            var err = document.getElementById('reminderFormError');
            if (err) { err.style.display = 'none'; err.textContent = ''; }

            populateChatsSelect();
            Array.from(document.getElementById('reminderChats').options).forEach(function (o) {
                o.selected = false;
            });
        }

        function fillReminderForm(r) {
            document.getElementById('reminderId').value = r.id || '';
            document.getElementById('reminderId').disabled = true;
            document.getElementById('reminderName').value = r.name || '';
            document.getElementById('reminderEnabled').checked = !!r.enabled;
            document.getElementById('reminderEnabled').setAttribute('aria-checked', r.enabled ? 'true' : 'false');
            document.getElementById('reminderCron').value = r.cron || '';
            document.getElementById('reminderTimezone').value = r.timezone || 'America/Sao_Paulo';
            document.getElementById('reminderMessage').value = r.message || '';
            document.getElementById('reminderMediaUrl').value = r.mediaUrl || '';
            document.getElementById('reminderTags').value = (r.tags || []).join(', ');

            var sel = document.getElementById('reminderChats');
            var alvo = new Set(r.targetChatIds || []);
            alvo.forEach(function (id) {
                if (!availableChatIds.includes(id)) {
                    var opt = document.createElement('option');
                    opt.value = id;
                    opt.textContent = id;
                    sel.appendChild(opt);
                }
            });
            Array.from(sel.options).forEach(function (o) {
                o.selected = alvo.has(o.value);
            });
        }

        window.saveReminder = async function (event) {
            event.preventDefault();

            var form = document.getElementById('reminderForm');
            var errDiv = document.getElementById('reminderFormError');

            var payload = {
                id: document.getElementById('reminderId').value.trim(),
                name: document.getElementById('reminderName').value.trim() || null,
                enabled: document.getElementById('reminderEnabled').checked,
                cron: document.getElementById('reminderCron').value.trim(),
                timezone: document.getElementById('reminderTimezone').value.trim() || null,
                message: document.getElementById('reminderMessage').value,
                mediaUrl: document.getElementById('reminderMediaUrl').value.trim() || null,
                targetChatIds: Array.from(document.getElementById('reminderChats').selectedOptions)
                    .map(function (o) { return o.value; }),
                tags: document.getElementById('reminderTags').value
                    .split(',').map(function (s) { return s.trim(); }).filter(Boolean)
            };

            var validationError = validateReminderPayload(payload, editingId === null);
            if (validationError) {
                errDiv.textContent = validationError;
                errDiv.style.display = 'block';
                return;
            }
            errDiv.style.display = 'none';

            try {
                if (editingId) {
                    await fetchJson(API + '/' + encodeURIComponent(editingId), {
                        method: 'PUT',
                        headers: Object.assign(
                            { 'Content-Type': 'application/json' },
                            csrfHeaders()
                        ),
                        body: JSON.stringify(payload)
                    });
                    showToast('Anúncio atualizado.', 'success');
                } else {
                    await fetchJson(API, {
                        method: 'POST',
                        headers: Object.assign(
                            { 'Content-Type': 'application/json' },
                            csrfHeaders()
                        ),
                        body: JSON.stringify(payload)
                    });
                    showToast('Anúncio criado.', 'success');
                }
                modal.hide();
                await loadReminders();
            } catch (e) {
                errDiv.textContent = e.message;
                errDiv.style.display = 'block';
            }
        };

        function validateReminderPayload(p, isNew) {
            if (!p.id) return 'ID é obrigatório.';
            if (!/^[a-z][a-z0-9-]*$/.test(p.id)) {
                return 'ID inválido: use minúsculas, comece com letra, sem espaços (ex: promo-sexta).';
            }
            if (!p.cron) return 'Cron é obrigatório.';
            if (p.cron.trim().split(/\s+/).length !== 6) {
                return 'Cron deve ter 6 campos (seg min hora dia mês diaSemana).';
            }
            if (!p.message || !p.message.trim()) return 'Mensagem é obrigatória.';
            if (!p.targetChatIds || p.targetChatIds.length === 0) {
                return 'Selecione pelo menos 1 chat alvo.';
            }
            return null;
        }

        window.toggleReminder = async function (id, enabled) {
            try {
                await fetchJson(API + '/' + encodeURIComponent(id) + '/toggle?enabled=' + enabled, {
                    method: 'POST',
                    headers: csrfHeaders()
                });
                showToast('Anúncio ' + (enabled ? 'habilitado' : 'desabilitado') + '.', 'success');
                await loadReminders();
                applyFeatureFlags();
            } catch (e) {
                showToast('Erro: ' + e.message, 'danger');
            }
        };

        window.deleteReminder = async function (id) {
            if (!confirm('Excluir o anúncio "' + id + '"?\n\nEssa ação remove o agendamento imediatamente.')) {
                return;
            }
            try {
                await fetchJson(API + '/' + encodeURIComponent(id), {
                    method: 'DELETE',
                    headers: csrfHeaders()
                });
                showToast('Anúncio excluído.', 'success');
                await loadReminders();
            } catch (e) {
                showToast('Erro: ' + e.message, 'danger');
            }
        };

        // ============================================================
        // TESTAR ANÚNCIO MANUALMENTE
        // ============================================================
        window.testReminder = async function (id) {
            // Pergunta para qual chat enviar o teste (default: showcase)
            const defaultChat = availableChatIds.length > 0 ? availableChatIds[0] : "";
            const chatId = prompt("Para qual Chat ID deseja enviar este teste?\n(Deixe vazio para usar o padrão do sistema)", defaultChat);

            if (chatId === null) return; // usuário cancelou

            const targetChat = chatId.trim();
            if (!targetChat) {
                showToast("Chat ID é obrigatório para teste.", "warning");
                return;
            }

            try {
                showToast("Enviando teste...", "info");
                await fetchJson(API + '/' + encodeURIComponent(id) + '/test?chatId=' + targetChat, {
                    method: 'POST',
                    headers: csrfHeaders()
                });
                showToast('✅ Teste enviado com sucesso!', 'success');
            } catch (e) {
                showToast('Erro ao testar: ' + e.message, 'danger');
            }
        };

        window.reloadRemindersFromDisk = async function () {
            if (!confirm('Recarregar os anúncios do disco?\n\nAlterações não salvas em memória serão perdidas.')) {
                return;
            }
            try {
                await fetchJson(API + '/reload', {
                    method: 'POST',
                    headers: csrfHeaders()
                });
                showToast('Anúncios recarregados do disco.', 'success');
                await loadReminders();
                applyFeatureFlags();
            } catch (e) {
                showToast('Erro: ' + e.message, 'danger');
            }
        };

        function loadRemindersDiagnostics() {
            var el = document.getElementById('remindersDiagnostics');
            if (!el) return;
            el.innerHTML = '<span class="spinner-border spinner-border-sm text-primary" role="status"></span>';
            fetchJson(API + '/status').then(function (s) {
                el.innerHTML =
                    '<div class="row g-2 small">' +
                    '<div class="col-6 col-md-3"><b>Total:</b> ' + s.total + '</div>' +
                    '<div class="col-6 col-md-3"><b>Habilitados:</b> ' + s.enabled + '</div>' +
                    '<div class="col-6 col-md-3"><b>Agendados:</b> ' + s.scheduled + '</div>' +
                    '<div class="col-12 col-md-3"><b>IDs agendados:</b> ' +
                    (s.scheduledIds || []).map(function (i) { return '<code>' + escapeHtml(i) + '</code>'; }).join(', ') +
                    '</div></div>';
            }).catch(function (e) {
                el.innerHTML = '<div class="alert alert-danger mb-0">Erro: ' + escapeHtml(e.message) + '</div>';
            });
        }

        document.addEventListener('DOMContentLoaded', function () {
            initRemindersModule();

            var tabReminders = document.getElementById('tab-reminders');
            if (tabReminders) {
                tabReminders.addEventListener('shown.bs.tab', function () {
                    loadReminders();
                    loadRemindersDiagnostics();
                });
            }

            var detailsDiag = document.querySelector('#reminders details');
            if (detailsDiag) {
                detailsDiag.addEventListener('toggle', function () {
                    if (detailsDiag.open) loadRemindersDiagnostics();
                });
            }
        });
    })();

    // ================================================================
    // FEATURE FLAGS — feedback visual (Fase 3)
    // ================================================================
    window.applyFeatureFlags = async function () {
        var flags;
        try {
            flags = await fetchJson('/admin-web/features');
        } catch (e) {
            console.warn('[feature-flags] falha ao carregar; mantendo estado atual:', e);
            return;
        }

        document.querySelectorAll('[data-feature]').forEach(function (el) {
            var key = el.getAttribute('data-feature');
            var info = flags[key];

            // Se a chave não existe no mapa, assume DESABILITADO (fail-safe)
            var enabled = info && info.enabled !== false;

            el.setAttribute('data-feature-disabled', enabled ? 'false' : 'true');
            el.title = enabled ? '' : 'Módulo desativado via Feature Flag';

            // Desabilita/ habilita controles internos
            el.querySelectorAll('button, input, select, textarea').forEach(function (control) {
                control.disabled = !enabled;
            });

            // Badge "🔒 Desativado" visível em touch
            var badge = el.querySelector(':scope > .card-header .feature-off-badge, '
                + ':scope > .feature-off-badge, '
                + ':scope > h6 > .feature-off-badge, '
                + ':scope > legend + .feature-off-badge');
            if (badge) {
                badge.hidden = enabled;
            }
        });

        // Persiste no DOM para consultas futuras (debug)
        document.documentElement.dataset.featureFlagsLoaded = 'true';
    };

    // ================================================================
    // INIT
    // ================================================================
    document.addEventListener('DOMContentLoaded', function () {
        updateThemeUI();

        // Fase 3: aplica o estado das feature flags em todos os data-feature
        applyFeatureFlags();

        var tabAniv = document.getElementById('tab-aniversarios');
        if (tabAniv) tabAniv.addEventListener('shown.bs.tab', loadBirthdays);

        var tabFeat = document.getElementById('tab-features');
        if (tabFeat) tabFeat.addEventListener('shown.bs.tab', loadFeatureFlags);
    });
})();