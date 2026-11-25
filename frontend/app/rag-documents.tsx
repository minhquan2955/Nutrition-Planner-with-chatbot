"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { api, ApiError } from "../lib/api";
import { Field, Notice } from "./components";

const documentsPath = "/admin/rag-documents";
const documentPath = (id: string) => `${documentsPath}/${encodeURIComponent(id)}`;

const statusLabels = {
  QUEUED: "Chờ xử lý", PROCESSING: "Đang xử lý", READY: "Chờ duyệt",
  ACTIVE: "Đang hoạt động", FAILED: "Xử lý lỗi", REVOKED: "Đã thu hồi",
};
const errorLabels: Record<string, string> = {
  PDF_INVALID: "Không mở được PDF. Hãy kiểm tra tệp gốc hoặc chọn một tệp khác.",
  PDF_PASSWORD_REQUIRED: "PDF yêu cầu mật khẩu. Hãy nạp bản có thể mở trực tiếp.",
  PDF_NO_TEXT: "Không tìm thấy lớp văn bản trên các trang đã chọn. Hãy dùng PDF có văn bản hoặc bản chép đã đối chiếu.",
  PDF_PAGE_OUT_OF_RANGE: "Số trang đã chọn vượt quá số trang của PDF.",
  PDF_STORAGE_ERROR: "Không đọc được tệp từ kho PDF.",
  PDF_TOO_COMPLEX: "Tài liệu vượt giới hạn xử lý. Hãy chọn ít trang hơn.",
  RAG_EMBEDDING_UNAVAILABLE: "Dịch vụ lập chỉ mục đang gián đoạn.",
  RAG_EMBEDDING_INVALID: "Dịch vụ lập chỉ mục trả về kết quả không hợp lệ.",
};
type RagDocument = {
  id: string;
  title: string;
  publisher: string;
  publicationYear: number | null;
  sourceUrl: string | null;
  version: string;
  status: keyof typeof statusLabels;
  chunkCount: number;
  errorCode: string | null;
  checksum: string;
};
type DocumentDetail = RagDocument & { chunks: {
  id?: string; pageStart: number; pageEnd: number; content: string; extractionMethod?: "PDF_TEXT" | "VERIFIED_TRANSCRIPTION";
}[] };

export function RagDocumentsScreen() {
  const [documents, setDocuments] = useState<RagDocument[]>([]);
  const [detail, setDetail] = useState<DocumentDetail | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [listVersion, setListVersion] = useState(0);
  const [listLoading, setListLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [operation, setOperation] = useState<"upload" | "activate" | "revoke" | null>(null);
  const [listError, setListError] = useState<unknown>(null);
  const [detailError, setDetailError] = useState<unknown>(null);
  const [uploadError, setUploadError] = useState<unknown>(null);
  const [actionError, setActionError] = useState<unknown>(null);
  const reviewHeading = useRef<HTMLHeadingElement>(null);
  const busy = operation !== null;
  const fields = uploadError instanceof ApiError ? uploadError.fieldErrors : {};
  const fieldState = (name: string, hasHint = false) => ({
    "aria-invalid": !!fields[name],
    "aria-describedby": [hasHint && `${name}-hint`, fields[name] && `${name}-error`].filter(Boolean).join(" ") || undefined,
  });

  useEffect(() => {
    let active = true;
    api<RagDocument[]>(documentsPath).then(items => {
      if (active) setDocuments(items);
    }).catch(error => {
      if (active) setListError(error);
    }).finally(() => {
      if (active) setListLoading(false);
    });
    return () => { active = false; };
  }, [listVersion]);

  useEffect(() => {
    if (!selectedId) return;
    const id = selectedId;
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    async function load() {
      try {
        const item = await api<DocumentDetail>(documentPath(id));
        if (!active) return;
        setDetail(item);
        setDocuments(items => items.map(document => document.id === item.id ? item : document));
        if (item.status === "QUEUED" || item.status === "PROCESSING") timer = setTimeout(load, 2000);
      } catch (error) {
        if (active) setDetailError(error);
      } finally {
        if (active) setDetailLoading(false);
      }
    }
    void load();
    return () => { active = false; clearTimeout(timer); };
  }, [selectedId, listVersion]);

  function select(id: string) {
    setDetail(null);
    setDetailError(null);
    setActionError(null);
    setSelectedId(id);
    setDetailLoading(true);
    if (selectedId === id) setListVersion(version => version + 1);
    reviewHeading.current?.focus();
  }

  function refresh() {
    setListError(null);
    setDetailError(null);
    setActionError(null);
    setListLoading(true);
    if (selectedId) setDetailLoading(true);
    setListVersion(version => version + 1);
  }

  async function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || listLoading) return;
    const form = event.currentTarget;
    const data = new FormData(form);
    const file = data.get("file");
    if (file instanceof File && file.size > 20 * 1024 * 1024) {
      setUploadError(new ApiError(400, "PDF_TOO_LARGE", "Tệp PDF vượt quá 20 MB.", { file: "Chọn tệp tối đa 20 MB." }));
      return;
    }
    for (const [name, value] of Array.from(data)) {
      if (typeof value === "string") {
        const trimmed = value.trim();
        if (trimmed) data.set(name, trimmed);
        else data.delete(name);
      }
    }
    setOperation("upload");
    setUploadError(null);
    try {
      const received = await api<RagDocument>(documentsPath, "POST", data);
      setDocuments(items => [received, ...items.filter(item => item.id !== received.id)]);
      form.reset();
      select(received.id);
    } catch (error) {
      setUploadError(error);
    } finally {
      setOperation(null);
    }
  }

  async function update(action: "activate" | "revoke") {
    if (!detail || busy || listLoading || detailLoading) return;
    if (action === "revoke" && !window.confirm("Thu hồi tài liệu này? Chatbot sẽ ngừng dùng tài liệu và người dùng sẽ không thể mở PDF từ trích dẫn cũ.")) return;
    setOperation(action);
    setActionError(null);
    try {
      const updated = await api<RagDocument>(`${documentPath(detail.id)}/${action}`, "POST");
      setDocuments(items => items.map(item => item.id === updated.id ? updated : item));
      setDetail({ ...detail, ...updated });
    } catch (error) {
      setActionError(error);
    } finally {
      setOperation(null);
    }
  }

  return <>
    <header className="page-heading">
      <div><h1>Quản lý tài liệu RAG</h1><p className="muted">Nạp PDF, đối chiếu đoạn trích với bản gốc, rồi kích hoạt tài liệu cho Chatbot.</p></div>
    </header>
    <form className="panel rag-upload" onSubmit={upload}>
      <h2>Nạp tài liệu</h2>
      <Notice error={uploadError} />
      <div className="form-grid">
        <Field name="title" label="Tiêu đề" errors={fields}><input id="title" name="title" required maxLength={250} {...fieldState("title")} /></Field>
        <Field name="publisher" label="Đơn vị phát hành" errors={fields}><input id="publisher" name="publisher" required maxLength={200} {...fieldState("publisher")} /></Field>
        <Field name="version" label="Phiên bản" errors={fields}><input id="version" name="version" required maxLength={80} {...fieldState("version")} /></Field>
        <Field name="publicationYear" label="Năm phát hành (tùy chọn)" errors={fields}>
          <input id="publicationYear" name="publicationYear" type="number" min={1} max={9999} {...fieldState("publicationYear")} />
        </Field>
        <Field name="sourceUrl" label="URL nguồn (tùy chọn)" errors={fields}>
          <input id="sourceUrl" name="sourceUrl" type="url" maxLength={2000} {...fieldState("sourceUrl")} />
        </Field>
        <Field name="pages" label="Trang PDF (tùy chọn)" errors={fields} hint="Ví dụ 14,25-26. Đếm từ trang đầu tiên của tệp. Để trống để đọc toàn bộ.">
          <input id="pages" name="pages" placeholder="14,25-26" {...fieldState("pages", true)} />
        </Field>
      </div>
      <Field name="file" label="Tệp PDF" errors={fields} hint="Tối đa 20 MB. Ưu tiên PDF có lớp văn bản.">
        <input id="file" name="file" type="file" accept=".pdf,application/pdf" required {...fieldState("file", true)} />
      </Field>
      <details className="rag-transcriptions" open={fields.transcriptions ? true : undefined}>
        <summary>Bản chép đối chiếu (tùy chọn)</summary>
        <Field name="transcriptions" label="Bản chép theo trang" errors={fields} hint={'Nhập JSON, ví dụ {"14":"Nội dung đã đối chiếu"}. Bản chép sẽ thay văn bản trích tự động ở các trang này.'}>
          <textarea id="transcriptions" name="transcriptions" rows={3} {...fieldState("transcriptions", true)} />
        </Field>
      </details>
      <button className="primary" disabled={busy || listLoading}>{operation === "upload" ? "Đang nạp PDF…" : "Nạp PDF"}</button>
    </form>
    <div className="rag-layout">
      <section className="panel rag-list" aria-labelledby="documents-heading">
        <div className="rag-actions">
          <h2 id="documents-heading">Tài liệu</h2>
          <button className="secondary" type="button" onClick={refresh} disabled={busy || listLoading || detailLoading}>Làm mới</button>
        </div>
        {listLoading && <p className="muted" role="status">Đang tải tài liệu…</p>}
        <Notice error={listError} />
        {!listLoading && !listError && documents.length === 0 && <p className="muted">Chưa có tài liệu.</p>}
        <ul className="rag-items">
          {documents.map(item => detail?.id === item.id ? detail : item).map(item => <li key={item.id}>
            <button className="rag-item" aria-current={selectedId === item.id ? "true" : undefined} disabled={busy} onClick={() => select(item.id)}>
              <strong>{item.title}</strong>
              <small>{statusLabels[item.status]} · {item.chunkCount} đoạn</small>
            </button>
          </li>)}
        </ul>
      </section>
      <section className="panel rag-review" aria-labelledby="review-heading">
        <h2 id="review-heading" tabIndex={-1} ref={reviewHeading}>{detail?.title ?? "Đối chiếu tài liệu"}</h2>
        {detailLoading && <p className="muted" role="status">Đang tải kết quả xử lý…</p>}
        <Notice error={detailError} />
        {!!detailError && <button className="text-button" onClick={refresh} disabled={busy || detailLoading}>Tải lại kết quả</button>}
        {!selectedId && <p className="muted">Chọn một tài liệu để xem trạng thái và đoạn trích.</p>}
        {detail && <>
          <p className="muted">{detail.publisher}{detail.publicationYear != null && ` · ${detail.publicationYear}`} · Phiên bản {detail.version}</p>
          {detail.sourceUrl && <p className="muted">URL nguồn <span>{detail.sourceUrl}</span></p>}
          <p className="muted"><small>SHA-256 <code>{detail.checksum}</code></small></p>
          <div className="rag-actions rag-document-actions">
            <span role="status" className="pill">{statusLabels[detail.status]} · {detail.chunkCount} đoạn</span>
            <a href={`/api/v1${documentPath(detail.id)}/file#page=1`} target="_blank" rel="noreferrer">Mở bản gốc ↗</a>
          </div>
          {detail.errorCode && <div className="notice error" role="alert">
            <strong>{errorLabels[detail.errorCode] ?? "Không xử lý được tài liệu này."}</strong>
            <small>Mã lỗi {detail.errorCode}</small>
          </div>}
          {detail.chunks.length > 0 && <>
            <h3>Đoạn trích để đối chiếu</h3>
            <ul className="rag-chunks">
              {detail.chunks.map((chunk, index) => <li className="chat-source" key={chunk.id ?? index}>
                <a href={`/api/v1${documentPath(detail.id)}/file#page=${chunk.pageStart}`} target="_blank" rel="noreferrer">
                  Mở PDF · trang {chunk.pageStart}{chunk.pageEnd > chunk.pageStart && `–${chunk.pageEnd}`} ↗
                </a>
                <small>{chunk.extractionMethod === "VERIFIED_TRANSCRIPTION" ? "Bản chép đã đối chiếu" : "Trích từ PDF"}</small>
                <p className="rag-text">{chunk.content}</p>
              </li>)}
            </ul>
          </>}
          <Notice error={actionError} />
          {operation === "activate" && <p className="muted" role="status">Đang kích hoạt tài liệu…</p>}
          {operation === "revoke" && <p className="muted" role="status">Đang thu hồi tài liệu…</p>}
          {detail.status === "READY" && <button className="primary" disabled={busy || listLoading || detailLoading} onClick={() => void update("activate")}>Kích hoạt</button>}
          {detail.status === "ACTIVE" && <button className="secondary" disabled={busy || listLoading || detailLoading} onClick={() => void update("revoke")}>Thu hồi</button>}
        </>}
      </section>
    </div>
  </>;
}
