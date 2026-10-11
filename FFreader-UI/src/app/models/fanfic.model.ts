export type FanficStatus = 'PENDING' | 'PARSING' | 'PARSED_PARTIALLY' | 'PARSED' | 'FAILED';

export interface Fanfic {
  id: string;
  sourceUrl: string;
  externalId: string;
  title: string | null;
  description: string | null;
  rating: string | null;
  language: string | null;
  genre: string | null;
  characters: string | null;
  chaptersCount: number | null;
  loadedChaptersCount: number | null;
  words: number | null;
  reviews: number | null;
  favs: number | null;
  follows: number | null;
  publishedAt: string | null;
  status: FanficStatus;
  errorMessage: string | null;
  downloadReady: boolean;
  createdAt: string;
  updatedAt: string | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}
