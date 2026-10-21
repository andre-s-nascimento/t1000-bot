-- Desabilita o acesso público via API
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transcripts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.releases_notified ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.birthdays ENABLE ROW LEVEL SECURITY;
-- Adicione outras tabelas do seu schema public, se houver