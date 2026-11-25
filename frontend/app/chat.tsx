"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { api } from "../lib/api";
import { Notice } from "./components";

type Conversation = { id: string; title: string; createdAt: string };
type Source = { id: string; title: string; version: string; pageStart: number; pageEnd: number; quotedText: string; available: boolean };
type Message = { id: string; role: "USER" | "ASSISTANT"; content: string; createdAt: string; sources: Source[] };
type Thread = Conversation & { messages: Message[] };

const conversationsPath = "/conversations";
const threadPath = (id: string) => `${conversationsPath}/${encodeURIComponent(id)}`;
const date = new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short", timeZone: "Asia/Ho_Chi_Minh" });

function newRequestId() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, "0")).join("");
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join("-");
}

export function ChatScreen() {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [historyError, setHistoryError] = useState<unknown>(null);
  const [historyVersion, setHistoryVersion] = useState(0);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [thread, setThread] = useState<Thread | null>(null);
  const [draft, setDraft] = useState("");
  const [busy, setBusy] = useState<"opening" | "sending" | null>(null);
  const [openError, setOpenError] = useState<unknown>(null);
  const [sendError, setSendError] = useState<unknown>(null);
  const question = useRef<HTMLTextAreaElement>(null);
  const log = useRef<HTMLDivElement>(null);
  const requestId = useRef<string | null>(null);

  useEffect(() => {
    if (log.current) log.current.scrollTop = log.current.scrollHeight;
  }, [thread]);

  useEffect(() => {
    let active = true;
    api<Conversation[]>(conversationsPath).then(items => {
      if (active) setConversations(items);
    }).catch(error => {
      if (active) setHistoryError(error);
    }).finally(() => {
      if (active) setHistoryLoading(false);
    });
    return () => { active = false; };
  }, [historyVersion]);

  async function open(id: string) {
    setBusy("opening");
    setOpenError(null);
    try {
      const item = await api<Thread>(threadPath(id));
      setThread(item);
      requestId.current = null;
      setDraft("");
      setSendError(null);
      question.current?.focus();
    } catch (error) {
      setOpenError(error);
    } finally {
      setBusy(null);
    }
  }

  function startNew() {
    setThread(null);
    requestId.current = null;
    setDraft("");
    setOpenError(null);
    setSendError(null);
    question.current?.focus();
  }

  async function send(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const content = draft.trim();
    if (busy || historyLoading || !content) return;
    setBusy("sending");
    setSendError(null);
    try {
      requestId.current ??= newRequestId();
      let current = thread;
      if (!current) {
        const created = await api<Conversation>(conversationsPath, "POST", { title: "" });
        current = { ...created, messages: [] };
        setThread(current);
        setConversations(items => [created, ...items]);
      }
      const updated = await api<Thread>(`${threadPath(current.id)}/messages`, "POST", { content, requestId: requestId.current });
      setThread(updated);
      setConversations(items => items.map(item => item.id === updated.id ? { id: updated.id, title: updated.title, createdAt: updated.createdAt } : item));
      setDraft("");
      requestId.current = null;
      question.current?.focus();
    } catch (error) {
      setSendError(error);
    } finally {
      setBusy(null);
    }
  }

  return <>
    <header className="page-heading">
      <div><h1>Chatbot dinh dưỡng</h1><p className="muted">Hỏi về dinh dưỡng và xem trang tài liệu làm căn cứ cho câu trả lời.</p></div>
    </header>
    <div className="rag-layout">
      <section className="panel rag-list" aria-labelledby="chat-history">
        <div className="rag-actions">
          <h2 id="chat-history">Hội thoại</h2>
          <button className="secondary" onClick={startNew} disabled={!!busy}>Hỏi mới</button>
        </div>
        {historyLoading && <p className="muted" role="status">Đang tải lịch sử…</p>}
        <Notice error={historyError} />
        {!!historyError && <button className="text-button" disabled={!!busy} onClick={() => {
          setHistoryError(null);
          setHistoryLoading(true);
          setHistoryVersion(version => version + 1);
        }}>Tải lại lịch sử</button>}
        {!historyLoading && !historyError && conversations.length === 0 && <p className="muted">Chưa có hội thoại.</p>}
        <ul className="rag-items">
          {conversations.map(item => <li key={item.id}>
            <button className="rag-item" aria-current={thread?.id === item.id ? "true" : undefined} onClick={() => void open(item.id)} disabled={!!busy}>
              <strong>{item.title}</strong>
              <time dateTime={item.createdAt}>{date.format(new Date(item.createdAt))}</time>
            </button>
          </li>)}
        </ul>
        <Notice error={openError} />
      </section>
      <section className="panel chat-thread" aria-labelledby="chat-thread-title">
        <h2 id="chat-thread-title">{thread?.title ?? "Câu hỏi mới"}</h2>
        <div ref={log} className="chat-messages" role="log" aria-label="Tin nhắn" tabIndex={0}>
          {!thread?.messages.length && <p className="muted">Bạn có thể hỏi về thành phần thực phẩm, khẩu phần hoặc dị ứng thực phẩm.</p>}
          {thread?.messages.map(message => <article key={message.id} className={message.role === "USER" ? "chat-message chat-user" : "chat-message"}>
            <strong className="chat-role">{message.role === "USER" ? "Bạn" : "Chatbot"}</strong>
            <p className="rag-text">{message.content}</p>
            {message.sources.length > 0 && <ul className="chat-sources" aria-label="Nguồn trích dẫn">
              {message.sources.map(source => <li key={source.id} className="chat-source">
                <strong>{source.title}</strong>
                <small>Phiên bản {source.version} · Trang PDF {source.pageStart}{source.pageEnd > source.pageStart && `–${source.pageEnd}`}</small>
                <blockquote className="rag-text">{source.quotedText}</blockquote>
                {source.available
                  ? <a href={`/api/v1${threadPath(thread.id)}/sources/${encodeURIComponent(source.id)}/file#page=${source.pageStart}`} target="_blank" rel="noreferrer">Mở PDF · trang {source.pageStart} ↗</a>
                  : <p className="muted">PDF không còn mở được.</p>}
              </li>)}
            </ul>}
          </article>)}
        </div>
        {busy && <p className="muted" role="status">{busy === "opening" ? "Đang mở hội thoại…" : "Chatbot đang tìm nguồn và trả lời…"}</p>}
        <Notice error={sendError} />
        <form onSubmit={send} className="chat-form">
          <label htmlFor="chat-question">Câu hỏi</label>
          <textarea ref={question} id="chat-question" rows={3} value={draft} readOnly={!!busy}
            required maxLength={4000} onChange={event => {
              requestId.current = null;
              setDraft(event.target.value);
            }} />
          <button className="primary" disabled={!!busy || historyLoading || !draft.trim()}>{sendError ? "Thử gửi lại" : "Gửi câu hỏi"}</button>
        </form>
      </section>
    </div>
  </>;
}
